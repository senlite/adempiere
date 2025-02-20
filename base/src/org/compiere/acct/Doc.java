/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.acct;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.adempiere.exceptions.DBException;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MConversionRate;
import org.compiere.model.MDocType;
import org.compiere.model.MFactAcct;
import org.compiere.model.MNote;
import org.compiere.model.MPeriod;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocumentEngine;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;
import org.compiere.util.Util;

/**
 *  Posting Document Root.
 *
 *  <pre>
 *  Table               Base Document Types (C_DocType.DocBaseType & AD_Reference_ID=183)
 *      Class           AD_Table_ID
 *  ------------------  ------------------------------
 *  C_Invoice:          ARI, ARC, ARF, API, APC
 *      Doc_Invoice     318 - has C_DocType_ID
 *
 *  C_Payment:          ARP, APP
 *      Doc_Payment     335 - has C_DocType_ID
 *
 *  C_Order:            SOO, POO,  POR (Requisition)
 *      Doc_Order       259 - has C_DocType_ID
 *
 *  M_InOut:            MMS, MMR
 *      Doc_InOut       319 - DocType derived
 *
 *  M_Inventory:        MMI
 *      Doc_Inventory   321 - DocType fixed
 *
 *  M_Movement:         MMM
 *      Doc_Movement    323 - DocType fixed
 *
 *  M_Production:       MMP
 *      Doc_Production  325 - DocType fixed
 *      
 * M_Production:        MMO
 *      Doc_CostCollector  330 - DocType fixed
 *
 *  C_BankStatement:    CMB
 *      Doc_Bank        392 - DocType fixed
 *
 *  C_Cash:             CMC
 *      Doc_Cash        407 - DocType fixed
 *
 *  C_Allocation:       CMA
 *      Doc_Allocation  390 - DocType fixed
 *
 *  GL_Journal:         GLJ
 *      Doc_GLJournal   224 = has C_DocType_ID
 *
 *  Matching Invoice    MXI
 *      M_MatchInv      472 - DocType fixed
 *
 *  Matching PO         MXP
 *      M_MatchPO       473 - DocType fixed
 *
 *	Project Issue		PJI
 *		C_ProjectIssue	623 - DocType fixed
 *	
 *  </pre>
 *  @author Jorg Janke
 *  @author victor.perez@e-evolution.com, e-Evolution http://www.e-evolution.com
 * 				<li>FR [ 2520591 ] Support multiples calendar for Org 
 *				@see http://sourceforge.net/tracker2/?func=detail&atid=879335&aid=2520591&group_id=176962
 *				<li>#1439 Reversed based on the accounting of the original document
 *				@see https://github.com/adempiere/adempiere/issues/1439
 *	@author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 *		<li> Add support to unidentified payments
 */
public abstract class Doc
{
	/** AD_Table_ID's of documents          */
	private static int[]  documentsTableID = null;
	
	/** Table Names of documents          */
	private static String[]  documentsTableName = null;

	/**************************************************************************
	 * 	 Document Types
	 *  --------------
	 *  C_DocType.DocBaseType & AD_Reference_ID=183
	 *  C_Invoice:          ARI, ARC, ARF, API, APC
	 *  C_Payment:          ARP, APP
	 *  C_Order:            SOO, POO
	 *  M_Transaction:      MMI, MMM, MMS, MMR
	 *  C_BankStatement:    CMB
	 *  C_Cash:             CMC
	 *  C_Allocation:       CMA
	 *  GL_Journal:         GLJ
	 *  C_ProjectIssue		PJI
	 *  M_Requisition		POR
	 *  M_ProductionBatch	MPO
	 **************************************************************************/

	/**	AR Invoices - ARI       */
	public static final String 	DOCTYPE_ARInvoice       = MDocType.DOCBASETYPE_ARInvoice;
	/**	AR Credit Memo          */
	public static final String 	DOCTYPE_ARCredit        = "ARC";
	/**	AR Receipt              */
	public static final String 	DOCTYPE_ARReceipt       = "ARR";
	/**	AR ProForma             */
	public static final String 	DOCTYPE_ARProForma      = "ARF";
	/**	AP Invoices             */
	public static final String 	DOCTYPE_APInvoice       = "API";
	/**	AP Credit Memo          */
	public static final String 	DOCTYPE_APCredit        = "APC";
	/**	AP Payment              */
	public static final String 	DOCTYPE_APPayment       = "APP";
	/**	CashManagement Bank Statement   */
	public static final String 	DOCTYPE_BankStatement   = "CMB";
	/**	CashManagement Cash Journals    */
	public static final String 	DOCTYPE_CashJournal     = "CMC";
	/**	CashManagement Allocations      */
	public static final String 	DOCTYPE_Allocation      = "CMA";
	/** Material Shipment       */
	public static final String 	DOCTYPE_MatShipment     = "MMS";
	/** Material Receipt        */
	public static final String 	DOCTYPE_MatReceipt      = "MMR";
	/** Material Inventory      */
	public static final String 	DOCTYPE_MatInventory    = "MMI";
	/** Material Movement       */
	public static final String 	DOCTYPE_MatMovement     = "MMM";
	/** Material Production     */
	public static final String 	DOCTYPE_MatProduction   = "MMP";
	/** Match Invoice           */
	public static final String 	DOCTYPE_MatMatchInv     = "MXI";
	/** Match PO                */
	public static final String 	DOCTYPE_MatMatchPO      = "MXP";
	/** GL Journal              */
	public static final String 	DOCTYPE_GLJournal       = "GLJ";
	/** Purchase Order          */
	public static final String 	DOCTYPE_POrder          = "POO";
	/** Sales Order             */
	public static final String 	DOCTYPE_SOrder          = "SOO";
	/** Project Issue           */
	public static final String	DOCTYPE_ProjectIssue	= "PJI";
	/** Purchase Requisition    */
	public static final String	DOCTYPE_PurchaseRequisition	= "POR";
	/** Planned Manufacturing Order    */
	public static final String	DOCTYPE_ManufacturingPlannedOrder = MDocType.DOCBASETYPE_ManufacturingPlannedOrder;

		
	//  Posting Status - AD_Reference_ID=234     //
	/**	Document Status         */
	public static final String 	STATUS_NotPosted        = "N";
	/**	Document Status         */
	public static final String 	STATUS_NotBalanced      = "b";
	/**	Document Status         */
	public static final String 	STATUS_NotConvertible   = "c";
	/**	Document Status         */
	public static final String 	STATUS_PeriodClosed     = "p";
	/**	Document Status         */
	public static final String 	STATUS_InvalidAccount   = "i";
	/**	Document Status         */
	public static final String 	STATUS_PostPrepared     = "y";
	/**	Document Status         */
	public static final String 	STATUS_Posted           = "Y";
	/**	Document Status         */
	public static final String 	STATUS_Error            = "E";

	
	/**
	 *  Create Posting document
	 *	@param ass accounting schema
	 *  @param AD_Table_ID Table ID of Documents
	 *  @param Record_ID record ID to load
	 *  @param trxName transaction name
	 *  @return Document or null
	 */
	public static Doc get (MAcctSchema[] ass, int AD_Table_ID, int Record_ID, String trxName) 
	{
		try {
			return new DocFactory()
					.withAccountingSchemes(ass)
					.withTableID(AD_Table_ID)
					.withRecordID(Record_ID)
					.withTrxName(trxName)
					.get();
		} catch (AdempiereUserError e) {
			s_log.log (Level.SEVERE, e.getMessage(), e);
			return null;
		}
	}	//	get
	
	/**
	 *  Create Posting document
	 *	@param ass accounting schema
	 *  @param AD_Table_ID Table ID of Documents
	 *  @param rs ResultSet
	 *  @param trxName transaction name
	 *  @return Document
	 * @throws AdempiereUserError 
	 */
	public static Doc get (MAcctSchema[] ass, int AD_Table_ID, ResultSet rs, String trxName) throws AdempiereUserError
	{
		
		return new DocFactory()
				.withAccountingSchemes(ass)
				.withTableID(AD_Table_ID)
				.withResultSet(rs)
				.withTrxName(trxName)
				.get();
	
	}   //  get

	/**
	 *  Post Document
	 * 	@param ass accounting schemata
	 * 	@param 	AD_Table_ID		Transaction table
	 *  @param  Record_ID       Record ID of this document
	 *  @param  force           force posting
	 *  @param trxName			transaction
	 *  @return null if the document was posted or error message
	 */
	public static String postImmediate (MAcctSchema[] ass, 
		int AD_Table_ID, int Record_ID, boolean force, String trxName)
	{
		Doc doc = get (ass, AD_Table_ID, Record_ID, trxName);
		if (doc != null)
			return doc.post (force, true);	//	repost
		return "NoDoc";
	}   //  post

	
    private static final String AND_PROCESSED_Y_AND_POSTED_N =
            " AND Processed='Y' AND Posted='N' ";

    /**
     * Returns a stream of record IDs for unposted documents at a particular 
     * date/time.  The date/time is defined by the ProcessedOn field which is 
     * the time in milliseconds.
     * @param ctx the properties containing the Client ID
     * @param tableName the table name to search
     * @param processedOn the ProcessedOn value to search for
     * @param trxName the transaction name 
     * @returns a stream of ids for the particular table.
     */
    public static Stream<Integer> streamUnpostedRecordIdsForTableOnDate(
            Properties ctx, String tableName, BigDecimal processedOn, 
            String trxName) {

        String where = "COALESCE(ProcessedOn, 0) =?"
                + AND_PROCESSED_Y_AND_POSTED_N;
        
        return new Query(ctx, tableName, where, trxName)
                        .setClient_ID()
                        .setOnlyActiveRecords(true)
                        .setParameters(processedOn)
                        .getIDsAsList()
                        .stream();

    }

    /**
     * Get an unsorted but distinct list of ProcessedOn values for the tables 
     * identified in the array of tableNames with the maximum ProcessedOn 
     * value less than the beforeTime value. If no ProcessedOn values are 
     * found, the list will contain a single entry, Env.ZERO.
     * @param ctx the properties containing the Client ID
     * @param tableNames an array of tableNames.  This should be a subset
     * of the Document table names.  If some other table name is passed, it
     * will be ignored.
     * @param beforeTime  The maximum ProcessedOn value to return.  If null, it 
     * will default to two seconds prior to the current time.
     * @param trxName the transaction name
     * @returns a list of ProcessedOn times which represent the time in 
     * milliseconds
     */
    public static List<BigDecimal> getListOfUnpostedProcessedOnDates(Properties ctx,
            final String[] tableNames, BigDecimal beforeTime,
            String trxName) {

        BigDecimal max = Optional.ofNullable(beforeTime)
                .orElse(TimeUtil.getTwoSecondsPriorToCurrentTimeInMillis());
        List<BigDecimal> listProcessedOn = new ArrayList<>();
        for (int i = 0; i < tableNames.length; i++) {
            String tableName = tableNames[i];
			//check if table is not null
			if (tableName == null)
				continue;

            String sql = "SELECT DISTINCT ProcessedOn"
                    + " FROM " + tableName
                    + " WHERE AD_Client_ID=? AND ProcessedOn<?"
                    + AND_PROCESSED_Y_AND_POSTED_N
                    + "  AND IsActive='Y'";
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                pstmt = DB.prepareStatement(sql, trxName);
                pstmt.setInt(1, Env.getAD_Client_ID(ctx));
                pstmt.setBigDecimal(2, max);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    BigDecimal processedOn = rs.getBigDecimal(1);
                    if (!listProcessedOn.contains(processedOn))
                        listProcessedOn.add(processedOn);
                }
            } catch (Exception e) {
                s_log.log(Level.SEVERE, sql, e);
            } finally {
                DB.close(rs, pstmt);
            }
        }

        if (listProcessedOn.isEmpty())
            listProcessedOn.add(Env.ZERO);
        return listProcessedOn;

    }

    /**
     * Saves the postedStatus flag and unlocks the document. This change is 
     * made directly in the database.  The Doc model should be reloaded,
     * if required, following this call.
     * @param doc The document for which the status will change
     * @param postStatus the posting status to set
     * @param trxName The transaction name to use
     */
    public static void savePostedStatus(Doc doc, String postStatus, String trxName) {
        
        requireNonNull(doc);
        setPostedStatusAndUnlock(doc.get_TableName(), doc.get_ID(), postStatus, trxName);

    }

	/**	Static Log						*/
	protected static CLogger	s_log = CLogger.getCLogger(Doc.class);
	/**	Log	per Document				*/
	protected CLogger			log = CLogger.getCLogger(getClass());

	/* If the transaction must be managed locally (false if it's managed externally by the caller) */ 
	private boolean m_manageLocalTrx;

	
	/**************************************************************************
	 *  Constructor
	 * 	@param ass accounting schemata
	 * 	@param clazz Document Class
	 * 	@param rs result set
	 * 	@param defaultDocumentType default document type or null
	 * 	@param trxName trx
	 */
	Doc (MAcctSchema[] ass, Class<?> clazz, ResultSet rs, String defaultDocumentType, String trxName)
	{
		p_Status = STATUS_Error;
		accountingSchemes = ass;
		m_ctx = new Properties(accountingSchemes[0].getCtx());
		m_ctx.setProperty("#AD_Client_ID", String.valueOf(accountingSchemes[0].getAD_Client_ID()));
		
		String className = clazz.getName();
		className = className.substring(className.lastIndexOf('.')+1);
		try
		{
			Constructor<?> constructor = clazz.getConstructor(new Class[]{Properties.class, ResultSet.class, String.class});
			p_po = (PO)constructor.newInstance(new Object[]{m_ctx, rs, trxName});
		}
		catch (Exception e)
		{
			String msg = className + ": " + e.getLocalizedMessage();
			log.severe(msg);
			throw new IllegalArgumentException(msg);
		}
		
		//	DocStatus
		//int index = p_po.get_ColumnIndex("DocStatus");
		//if (index != -1)
		//	m_DocStatus = (String)p_po.get_Value(index);
		getDocStatus();
		
		//	Document Type
		setDocumentType (defaultDocumentType);
		m_trxName = trxName;
		m_manageLocalTrx = false;
		if (m_trxName == null) {
			m_trxName = "Post" + m_DocumentType + p_po.get_ID();
			m_manageLocalTrx = true;
		}
		p_po.set_TrxName(m_trxName);

		//	Amounts
		m_Amounts[0] = Env.ZERO;
		m_Amounts[1] = Env.ZERO;
		m_Amounts[2] = Env.ZERO;
		m_Amounts[3] = Env.ZERO;
	}   //  Doc

	/** Accounting Schema Array     */
	private MAcctSchema[]    	accountingSchemes = null;
	/** Properties					*/
	private Properties			m_ctx = null;
	/** Transaction Name			*/
	private String				m_trxName = null;
	/** The Document				*/
	protected PO				p_po = null;
	/** Document Type      			*/
	private String				m_DocumentType = null;
	/** Document Status      			*/
	private String				m_DocStatus = null;
	/** Document No      			*/
	private String				m_DocumentNo = null;
	/** Description      			*/
	private String				m_Description = null;
	/** GL Category      			*/
	private int					m_GL_Category_ID = 0;
	/** GL Period					*/
	private MPeriod 			m_period = null;
	/** Period ID					*/
	private int					m_C_Period_ID = 0;
	/** Location From				*/
	private int					m_C_LocFrom_ID = 0;
	/** Location To					*/
	private int					m_C_LocTo_ID = 0;
	/** Accounting Date				*/
	private Timestamp			m_DateAcct = null;
	/** Document Date				*/
	private Timestamp			m_DateDoc = null;
	/** Tax Included				*/
	private boolean				m_TaxIncluded = false;
	/** Is (Source) Multi-Currency Document - i.e. the document has different currencies
	 *  (if true, the document will not be source balanced)     */
	private boolean				m_MultiCurrency = false;
	/** BP Sales Region    			*/
	private int					m_BP_C_SalesRegion_ID = -1;
	/** B Partner	    			*/
	private int					m_C_BPartner_ID = -1;

	/** Bank Account				*/
	private int 				m_C_BankAccount_ID = -1;
	/** Cach Book					*/
	private int 				m_C_CashBook_ID = -1;
	/** Currency					*/
	private int					m_C_Currency_ID = -1;

	/**	Contained Doc Lines			*/
	protected DocLine[]			p_lines;

	/** Facts                       */
	private ArrayList<Fact>    	m_fact = null;

	/** No Currency in Document Indicator (-1)	*/
	protected static final int  NO_CURRENCY = -2;
	
	/**	Actual Document Status  */
	protected String			p_Status = null;
	public String getPostStatus() {
		return p_Status;
	}

	/** Error Message			*/
	protected String			p_Error = null;
	
	
	/**
	 * 	Get Context
	 *	@return context
	 */
	public Properties getCtx()
	{
		return m_ctx;
	}	//	getCtx

	/**
	 * 	Get Table Name
	 *	@return table name
	 */
	public String get_TableName()
	{
		return p_po.get_TableName();
	}	//	get_TableName
	
	/**
	 * 	Get Table ID
	 *	@return table id
	 */
	public int get_Table_ID()
	{
		return p_po.get_Table_ID();
	}	//	get_Table_ID

	/**
	 * 	Get Record_ID
	 *	@return record id
	 */
	public int get_ID()
	{
		return p_po.get_ID();
	}	//	get_ID
	
	/**
	 * 	Get Persistent Object
	 *	@return po
	 */
	protected PO getPO()
	{
		return p_po;
	}	//	getPO
	
	public final String postImmediate(boolean force) {
	    
        return post (force, true);  //  repost
        
	}
	
	/**
	 *  Post Document.
	 *  <pre>
	 *  - try to lock document (Processed='Y' (AND Processing='N' AND Posted='N'))
	 * 		- if not ok - return false
	 *          - postlogic (for all Accounting Schema)
	 *              - create Fact lines
	 *          - postCommit
	 *              - commits Fact lines and Document & sets Processing = 'N'
	 *              - if error - create Note
	 *  </pre>
	 *  @param force if true ignore that locked
	 *  @param repost if true ignore that already posted
	 *  @return null if posted error otherwise
	 */
	public final String post (boolean force, boolean repost)
	{
		final String docStatus = getDocStatus();
        if (docStatus == null)
			;	//	return "No DocStatus for DocumentNo=" + getDocumentNo();
		else if (docStatus.equals(DocumentEngine.STATUS_Completed)
			|| docStatus.equals(DocumentEngine.STATUS_Closed)
			|| docStatus.equals(DocumentEngine.STATUS_Voided)
			|| docStatus.equals(DocumentEngine.STATUS_Reversed))
			;
		else
			return "Invalid DocStatus='" + docStatus + "' for DocumentNo=" + getDocumentNo();
		//
		if (p_po.getAD_Client_ID() != accountingSchemes[0].getAD_Client_ID())
		{
			String error = "AD_Client_ID Conflict - Document=" + p_po.getAD_Client_ID()
				+ ", AcctSchema=" + accountingSchemes[0].getAD_Client_ID();
			log.severe(error);
			return error;
		}
		
		if (!lock(force, repost)) {
			if (force)
				return "Cannot Lock - ReSubmit";
			return "Cannot Lock - ReSubmit or RePost with Force";
		}
		
		p_Error = loadDocumentDetails();
		if (p_Error != null)
			return p_Error;

		//  Delete existing Accounting
		if (repost)
		{
			if (isPosted() && !isPeriodOpen())	//	already posted - don't delete if period closed
			{
				log.log(Level.SEVERE, toString() + " - Period Closed for already posed document");
				unlock();
				return "PeriodClosed";
			}
			//	delete it
			deleteAcct();
		}
		else if (isPosted())
		{
			log.log(Level.SEVERE, toString() + " - Document already posted");
			unlock();
			return "AlreadyPosted";
		}
		
		p_Status = STATUS_NotPosted;

		//  Create Fact per AcctSchema
		m_fact = new ArrayList<Fact>();

		//  for all Accounting Schema
		boolean OK = true;
		getPO().setDoc(this);
		try
		{
			for (int i = 0; OK && i < accountingSchemes.length; i++)
			{
				//	if acct schema has "only" org, skip
				boolean skip = false;
				if (accountingSchemes[i].getAD_OrgOnly_ID() != 0)
				{
					//	Header Level Org
					skip = accountingSchemes[i].isSkipOrg(getAD_Org_ID());
					//	Line Level Org
					if (p_lines != null)
					{
						for (int line = 0; skip && line < p_lines.length; line++)
						{
							skip = accountingSchemes[i].isSkipOrg(p_lines[line].getAD_Org_ID());
							if (!skip)
								break;
						}
					}
				}
				if (skip)
					continue;
				//	post
				log.info("(" + i + ") " + p_po);
				p_Status = postLogic (i);
				if (!p_Status.equals(STATUS_Posted))
					OK = false;
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "", e);
			p_Status = STATUS_Error;
			p_Error = e.toString();
			OK = false;
		}

		String validatorMsg = null;
		// Call validator on before post
		if (p_Status.equals(STATUS_Posted)) {			
			validatorMsg = ModelValidationEngine.get().fireDocValidate(getPO(), ModelValidator.TIMING_BEFORE_POST);
			if (validatorMsg != null) {
				p_Status = STATUS_Error;
				p_Error = validatorMsg;
				OK = false;
			}
		}

		//  commitFact
		p_Status = postCommit (p_Status);

		if (p_Status.equals(STATUS_Posted)) {
			validatorMsg = ModelValidationEngine.get().fireDocValidate(getPO(), ModelValidator.TIMING_AFTER_POST);
			if (validatorMsg != null) {
				p_Status = STATUS_Error;
				p_Error = validatorMsg;
				OK = false;
			}
		}
		  
		//  Create Note
		if (!p_Status.equals(STATUS_Posted))
		{
			//  Insert Note
			String AD_MessageValue = "PostingError-" + p_Status;
			int AD_User_ID = p_po.getUpdatedBy();
			MNote note = new MNote (getCtx(), AD_MessageValue, AD_User_ID, 
				getAD_Client_ID(), getAD_Org_ID(), null);
			note.setRecord(p_po.get_Table_ID(), p_po.get_ID());
			//  Reference
			note.setReference(toString());	//	Document
			//	Text
			StringBuffer Text = new StringBuffer (Msg.getMsg(Env.getCtx(), AD_MessageValue));
			if (p_Error != null)
				Text.append(" (").append(p_Error).append(")");
			String cn = getClass().getName();
			Text.append(" - ").append(cn.substring(cn.lastIndexOf('.')))
				.append(" (").append(getDocumentType())
				.append(" - DocumentNo=").append(getDocumentNo())
				.append(", DateAcct=").append(getDateAcct().toString().substring(0,10))
				.append(", Amount=").append(getAmount())
				.append(", Sta=").append(p_Status)
				.append(" - PeriodOpen=").append(isPeriodOpen())
				.append(", Balanced=").append(isBalanced());
			note.setTextMsg(Text.toString());
			note.saveEx();
			p_Error = Text.toString();
		}

		//  dispose facts
		for (int i = 0; i < m_fact.size(); i++)
		{
			Fact fact = m_fact.get(i);
			if (fact != null)
				fact.dispose();
		}
		p_lines = null;

		if (p_Status.equals(STATUS_Posted))
			return null;
		return p_Error;
	}   //  post

    boolean lock(boolean force, boolean repost) {

        String trxName = null;  //  outside trx if on server
        if (! m_manageLocalTrx)
            trxName = getTrxName(); // on trx if it's in client

        StringBuilder sql = new StringBuilder ("UPDATE ");
		sql.append(get_TableName()).append( " SET Processing='Y' WHERE ")
			.append(get_TableName()).append("_ID=").append(get_ID())
			.append(" AND Processed='Y' AND IsActive='Y'");
		if (!force)
			sql.append(" AND (Processing='N' OR Processing IS NULL)");
		if (!repost)
			sql.append(" AND Posted='N'");
		boolean locked = DB.executeUpdate(sql.toString(), trxName) == 1;
		if (locked)
		    log.info("Locked: " + get_TableName() + "_ID=" + get_ID());
        else
            log.log(Level.SEVERE, "Resubmit - Cannot lock " + get_TableName() 
                + "_ID=" + get_ID() + ", Force=" + force 
                + ",RePost=" + repost);
		
		return locked;
		    
    }

	/**
     *  Unlock Document
     */
    void unlock()
    {
    	String trxName = null;	//	outside trx if on server
    	if (! m_manageLocalTrx)
    		trxName = getTrxName(); // on trx if it's in client
    	StringBuilder sql = new StringBuilder ("UPDATE ");
    	sql.append(get_TableName()).append( " SET Processing='N' WHERE ")
    		.append(get_TableName()).append("_ID=").append(p_po.get_ID());
    	DB.executeUpdate(sql.toString(), trxName);
    }   //  unlock

    /**
	 * 	Delete Accounting
	 *	@return number of records
	 */
	int deleteAcct()
	{
		String sql = "DELETE Fact_Acct"
		        + " WHERE AD_Table_ID=" + get_Table_ID()
		        + " AND Record_ID=" + p_po.get_ID();
		int no = DB.executeUpdate(sql, getTrxName());
		if (no != 0)
			log.info("deleted=" + no);
		return no;
	}	//	deleteAcct

	/**
	 *  Posting logic for Accounting Schema index
	 *  @param  index   Accounting Schema index
	 *  @return posting status/error code
	 */
	private final String postLogic (int index)
	{
		log.info("(" + index + ") " + p_po);
		
		//  rejectUnbalanced
		if (!accountingSchemes[index].isSuspenseBalancing() && !isBalanced())
			return STATUS_NotBalanced;

		//  rejectUnconvertible
		if (!isConvertible(accountingSchemes[index]))
			return STATUS_NotConvertible;

		//  rejectPeriodClosed
		if (!isPeriodOpen())
			return STATUS_PeriodClosed;

		if (isReversed() && IsReverseGenerated() && isReverseWithOriginalAccounting())
			return generateReverseWithOriginalAccounting(accountingSchemes[index]);

		//  createFacts
		ArrayList<Fact> facts = createFacts (accountingSchemes[index]);
		if (facts == null)
			return STATUS_Error;
		
		// call modelValidator
		String validatorMsg = ModelValidationEngine.get().fireFactsValidate(accountingSchemes[index], facts, getPO());
		if (validatorMsg != null) {
			p_Error = validatorMsg;
			return STATUS_Error;
		}
		
		for (int f = 0; f < facts.size(); f++)
		{
			Fact fact = facts.get(f);
			if (fact == null)
				return STATUS_Error;
			m_fact.add(fact);
			//
			p_Status = STATUS_PostPrepared;

			//	check accounts
			if (!fact.checkAccounts())
				return STATUS_InvalidAccount;
			
			//	distribute
			if (!fact.distribute())
				return STATUS_Error;
			
			//  balanceSource
			if (!fact.isSourceBalanced())
			{
				fact.balanceSource();
				if (!fact.isSourceBalanced())
					return STATUS_NotBalanced;
			}

			//  balanceSegments
			if (!fact.isSegmentBalanced())
			{
				fact.balanceSegments();
				if (!fact.isSegmentBalanced())
					return STATUS_NotBalanced;
			}

			//  balanceAccounting
			if (!fact.isAcctBalanced())
			{
				fact.balanceAccounting();
				if (!fact.isAcctBalanced())
					return STATUS_NotBalanced;
			}
			
		}	//	for all facts
		
		return STATUS_Posted;
	}   //  postLogic

	/**
	 *  Post Commit.
	 *  Save Facts & Document
	 *  @param status status
	 *  @return Posting Status
	 */
	private final String postCommit (String status)
	{
		log.info("Sta=" + status + " DT=" + getDocumentType() 
			+ " ID=" +  p_po.get_ID());
		p_Status = status;

		Trx trx = Trx.get(getTrxName(), true);
		try
		{
		//  *** Transaction Start       ***
			//  Commit Facts
			if (status.equals(STATUS_Posted))
			{
				for (int i = 0; i < m_fact.size(); i++)
				{
					Fact fact = m_fact.get(i);
					if (fact == null)
						;
					else if (fact.save(getTrxName()))
						;
					else
					{
						log.log(Level.SEVERE, "(fact not saved) ... rolling back");
						if (m_manageLocalTrx) {
							trx.rollback();
							trx.close();
						}
						unlock();
						return STATUS_Error;
					}
				}
			}
			//  Commit Doc
			if (!save(getTrxName()))     //  contains unlock & document status update
			{
				log.log(Level.SEVERE, "(doc not saved) ... rolling back");
				if (m_manageLocalTrx) {
					trx.rollback();
					trx.close();
				}
				unlock();
				return STATUS_Error;
			}
			//	Success
			if (m_manageLocalTrx) {
				trx.commit(true);
				trx.close();
				trx = null;
			}
		//  *** Transaction End         ***
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "... rolling back", e);
			status = STATUS_Error;
			if (m_manageLocalTrx) {
				try {
					if (trx != null)
						trx.rollback();
				} catch (Exception e2) {}
				try {
					if (trx != null)
						trx.close();
					trx = null;
				} catch (Exception e3) {}
			}
			unlock();
		}
		p_Status = status;
		return status;
	}   //  postCommit

	/**
	 * 	Get Trx Name and create Transaction
	 *	@return Trx Name
	 */
	public String getTrxName()
	{
		return m_trxName;
	}	//	getTrxName
	
	/**************************************************************************
	 *  Load Document Type and GL Info.
	 * 	Set p_DocumentType and p_GL_Category_ID
	 * 	@return document type (i.e. C_DocType.DocBaseType)
	 */
	public String getDocumentType()
	{
		if (m_DocumentType == null)
			setDocumentType(null);
		return m_DocumentType;
	}   //  getDocumentType

	/**
	 *  Load Document Type and GL Info.
	 * 	Set p_DocumentType and p_GL_Category_ID
	 *	@param DocumentType
	 */
	public void setDocumentType (String DocumentType)
	{
		if (DocumentType != null)
			m_DocumentType = DocumentType;
		//  No Document Type defined
		if (m_DocumentType == null && getC_DocType_ID() != 0)
		{
			String sql = "SELECT DocBaseType, GL_Category_ID FROM C_DocType WHERE C_DocType_ID=?";
			PreparedStatement pstmt = null;
			ResultSet rsDT = null;
			try
			{
				pstmt = DB.prepareStatement(sql, null);
				pstmt.setInt(1, getC_DocType_ID());
				rsDT = pstmt.executeQuery();
				if (rsDT.next())
				{
					m_DocumentType = rsDT.getString(1);
					m_GL_Category_ID = rsDT.getInt(2);
				}
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
			finally
			{
				DB.close(rsDT, pstmt);
				rsDT = null; 
				pstmt = null;
			}
		}
		if (m_DocumentType == null)
		{
			log.log(Level.SEVERE, "No DocBaseType for C_DocType_ID=" 
				+ getC_DocType_ID() + ", DocumentNo=" + getDocumentNo());
		}

		//  We have a document Type, but no GL info - search for DocType
		if (m_GL_Category_ID == 0)
		{
			String sql = "SELECT GL_Category_ID FROM C_DocType "
				+ "WHERE AD_Client_ID=? AND DocBaseType=?";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, null);
				pstmt.setInt(1, getAD_Client_ID());
				pstmt.setString(2, m_DocumentType);
				ResultSet rsDT = pstmt.executeQuery();
				if (rsDT.next())
					m_GL_Category_ID = rsDT.getInt(1);
				rsDT.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}

		//  Still no GL_Category - get Default GL Category
		if (m_GL_Category_ID == 0)
		{
			String sql = "SELECT GL_Category_ID FROM GL_Category "
				+ "WHERE AD_Client_ID=? "
				+ "ORDER BY IsDefault DESC";
			try
			{
				PreparedStatement pstmt = DB.prepareStatement(sql, null);
				pstmt.setInt(1, getAD_Client_ID());
				ResultSet rsDT = pstmt.executeQuery();
				if (rsDT.next())
					m_GL_Category_ID = rsDT.getInt(1);
				rsDT.close();
				pstmt.close();
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
		}
		//
		if (m_GL_Category_ID == 0)
			log.log(Level.SEVERE, "No default GL_Category - " + toString());

		if (m_DocumentType == null)
			throw new IllegalStateException("Document Type not found");
	}	//	setDocumentType

	
	/**************************************************************************
	 *  Is the Source Document Balanced
	 *  @return true if (source) balanced
	 */
	public boolean isBalanced()
	{
		//  Multi-Currency documents are source balanced by definition
		if (isMultiCurrency())
			return true;
		//
		boolean retValue = getBalance().signum() == 0;
		if (retValue)
			log.fine("Yes " + toString());
		else
			log.warning("NO - " + toString());
		return retValue;
	}	//	isBalanced

	/**
	 *  Is Document convertible to currency and Conversion Type
	 *  @param acctSchema accounting schema
	 *  @return true, if convertible to accounting currency
	 */
	public boolean isConvertible (MAcctSchema acctSchema)
	{
		//  No Currency in document
		if (getC_Currency_ID() == NO_CURRENCY)
		{
			log.fine("(none) - " + toString());
			return true;
		}
		// Journal from a different acct schema
		if (this instanceof Doc_GLJournal) {
			int glj_as = ((Integer) p_po.get_Value("C_AcctSchema_ID")).intValue();
			if (acctSchema.getC_AcctSchema_ID() != glj_as)
				return true;
		}
		//  Get All Currencies
		HashSet<Integer> set = new HashSet<Integer>();
		set.add(new Integer(getC_Currency_ID()));
		for (int i = 0; p_lines != null && i < p_lines.length; i++)
		{
			int C_Currency_ID = p_lines[i].getC_Currency_ID();
			if (C_Currency_ID != NO_CURRENCY)
				set.add(new Integer(C_Currency_ID));
		}

		//  just one and the same
		if (set.size() == 1 && acctSchema.getC_Currency_ID() == getC_Currency_ID())
		{
			log.fine("(same) Cur=" + getC_Currency_ID() + " - " + toString());
			return true;
		}

		boolean convertible = true;
		Iterator<Integer> it = set.iterator();
		while (it.hasNext() && convertible)
		{
			int C_Currency_ID = it.next().intValue();
			if (C_Currency_ID != acctSchema.getC_Currency_ID())
			{
				BigDecimal amt = MConversionRate.getRate (C_Currency_ID, acctSchema.getC_Currency_ID(),
					getDateAcct(), getC_ConversionType_ID(), getAD_Client_ID(), getAD_Org_ID());
				if (amt == null)
				{
					convertible = false;
					log.warning ("NOT from C_Currency_ID=" + C_Currency_ID
						+ " to " + acctSchema.getC_Currency_ID()
						+ " - " + toString());
				}
				else
					log.fine("From C_Currency_ID=" + C_Currency_ID);
			}
		}

		log.fine("Convertible=" + convertible + ", AcctSchema C_Currency_ID=" + acctSchema.getC_Currency_ID() + " - " + toString());
		return convertible;
	}	//	isConvertible

	/**
	 *  Calculate Period from DateAcct.
	 *  m_C_Period_ID is set to -1 of not open to 0 if not found
	 */
	public void setPeriod()
	{
		if (m_period != null)
			return;
		
		//	Period defined in GL Journal (e.g. adjustment period)
		int index = p_po.get_ColumnIndex("C_Period_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				m_period = MPeriod.get(getCtx(), ii.intValue());
		}
		if (m_period == null)
			m_period = MPeriod.get(getCtx(), getDateAcct(), getAD_Org_ID());
		//	Is Period Open?
		if (m_period != null 
			&& m_period.isOpen(getDocumentType(), getDateAcct()))
			m_C_Period_ID = m_period.getC_Period_ID();
		else
			m_C_Period_ID = -1;
		//
		log.fine(	// + AD_Client_ID + " - " 
			getDateAcct() + " - " + getDocumentType() + " => " + m_C_Period_ID);
	}   //  setC_Period_ID

	/**
	 * 	Get C_Period_ID
	 *	@return period
	 */
	public int getC_Period_ID()
	{
		if (m_period == null)
			setPeriod();
		return m_C_Period_ID;
	}	//	getC_Period_ID

	/**
	 *	Is Period Open
	 *  @return true if period is open
	 */
	public boolean isPeriodOpen()
	{
		setPeriod();
		boolean open = m_C_Period_ID > 0;
		if (open)
			log.fine("Yes - " + toString());
		else
			log.warning("NO - " + toString());
		return open;
	}	//	isPeriodOpen

	/*************************************************************************/

	/**	Amount Type - Invoice - Gross   */
	public static final int 	AMTTYPE_Gross   = 0;
	/**	Amount Type - Invoice - Net   */
	public static final int 	AMTTYPE_Net     = 1;
	/**	Amount Type - Invoice - Charge   */
	public static final int 	AMTTYPE_Charge  = 2;

	/** Source Amounts (may not all be used)	*/
	private BigDecimal[]		m_Amounts = new BigDecimal[4];
	/** Quantity								*/
	private BigDecimal			m_qty = null;

	/**
	 *	Get the Amount
	 *  (loaded in loadDocumentDetails)
	 *
	 *  @param AmtType see AMTTYPE_*
	 *  @return Amount
	 */
	public BigDecimal getAmount(int AmtType)
	{
		if (AmtType < 0 || AmtType >= m_Amounts.length)
			return null;
		return m_Amounts[AmtType];
	}	//	getAmount

	/**
	 *	Set the Amount
	 *  @param AmtType see AMTTYPE_*
	 *  @param amt Amount
	 */
	public void setAmount(int AmtType, BigDecimal amt)
	{
		if (AmtType < 0 || AmtType >= m_Amounts.length)
			return;
		if (amt == null)
			m_Amounts[AmtType] = Env.ZERO;
		else
			m_Amounts[AmtType] = amt;
	}	//	setAmount

	/**
	 *  Get Amount with index 0
	 *  @return Amount (primary document amount)
	 */
	public BigDecimal getAmount()
	{
		return m_Amounts[0];
	}   //  getAmount

	/**
	 *  Set Quantity
	 *  @param qty Quantity
	 */
	public void setQty (BigDecimal qty)
	{
		m_qty = qty;
	}   //  setQty

	/**
	 *  Get Quantity
	 *  @return Quantity
	 */
	public BigDecimal getQty()
	{
		if (m_qty == null)
		{
			int index = p_po.get_ColumnIndex("Qty");
			if (index != -1)
				m_qty = (BigDecimal)p_po.get_Value(index);
			else
				m_qty = Env.ZERO;
		}
		return m_qty;
	}   //  getQty
	
	/*************************************************************************/

	/**	Account Type - Invoice - Charge  */
	public static final int 	ACCTTYPE_Charge         = 0;
	/**	Account Type - Invoice - AR  */
	public static final int 	ACCTTYPE_C_Receivable   = 1;
	/**	Account Type - Invoice - AP  */
	public static final int 	ACCTTYPE_V_Liability    = 2;
	/**	Account Type - Invoice - AP Service  */
	public static final int 	ACCTTYPE_V_Liability_Services    = 3;
	/**	Account Type - Invoice - AR Service  */
	public static final int 	ACCTTYPE_C_Receivable_Services   = 4;

	/** Account Type - Payment - Unallocated */
	public static final int     ACCTTYPE_UnallocatedCash = 10;
	/** Account Type - Payment - Transfer */
	public static final int 	ACCTTYPE_BankInTransit  = 11;
	/** Account Type - Payment - Selection */
	public static final int     ACCTTYPE_PaymentSelect  = 12;
	/** Account Type - Payment - Prepayment */
	public static final int 	ACCTTYPE_C_Prepayment  = 13;
	/** Account Type - Payment - Prepayment */
	public static final int     ACCTTYPE_V_Prepayment  = 14;
	/**	Account Type - payment - Unidentified */
	public static final int     ACCTTYPE_BankUnidentified = 15;

	/** Account Type - Cash     - Asset */
	public static final int     ACCTTYPE_CashAsset = 20;
	/** Account Type - Cash     - Transfer */
	public static final int     ACCTTYPE_CashTransfer = 21;
	/** Account Type - Cash     - Expense */
	public static final int     ACCTTYPE_CashExpense = 22;
	/** Account Type - Cash     - Receipt */
	public static final int     ACCTTYPE_CashReceipt = 23;
	/** Account Type - Cash     - Difference */
	public static final int     ACCTTYPE_CashDifference = 24;

	/** Account Type - Allocation - Discount Expense (AR) */
	public static final int 	ACCTTYPE_DiscountExp = 30;
	/** Account Type - Allocation - Discount Revenue (AP) */
	public static final int 	ACCTTYPE_DiscountRev = 31;
	/** Account Type - Allocation  - Write Off */
	public static final int 	ACCTTYPE_WriteOff = 32;

	/** Account Type - Bank Statement - Asset  */
	public static final int     ACCTTYPE_BankAsset = 40;
	/** Account Type - Bank Statement - Interest Revenue */
	public static final int     ACCTTYPE_InterestRev = 41;
	/** Account Type - Bank Statement - Interest Exp  */
	public static final int     ACCTTYPE_InterestExp = 42;

	/** Inventory Accounts  - Differences	*/
	public static final int     ACCTTYPE_InvDifferences = 50;
	/** Inventory Accounts - NIR		*/
	public static final int     ACCTTYPE_NotInvoicedReceipts = 51;

	/** Project Accounts - Assets      	*/
	public static final int     ACCTTYPE_ProjectAsset = 61;
	/** Project Accounts - WIP         	*/
	public static final int     ACCTTYPE_ProjectWIP = 62;

	/** GL Accounts - PPV Offset		*/
	public static final int     ACCTTYPE_PPVOffset = 101;
	/** GL Accounts - Commitment Offset	*/
	public static final int     ACCTTYPE_CommitmentOffset = 111;
	/** GL Accounts - Commitment Offset	Sales */
	public static final int     ACCTTYPE_CommitmentOffsetSales = 112;


	/**
	 *	Get the Valid Combination id for Accounting Schema
	 *  @param acctType see ACCTTYPE_*
	 *  @param acctSchema accounting schema
	 *  @return C_ValidCombination_ID
	 */
	public int getValidCombinationId(int acctType, MAcctSchema acctSchema)
	{
		int para_1 = 0;     //  first parameter (second is always AcctSchema)
		String sql = null;

		/**	Account Type - Invoice */
		if (acctType == ACCTTYPE_Charge)	//	see getChargeAccount in DocLine
		{
			int cmp = getAmount(AMTTYPE_Charge).compareTo(Env.ZERO);
			if (cmp == 0)
				return 0;
			else if (cmp < 0)
				sql = "SELECT CH_Expense_Acct FROM C_Charge_Acct WHERE C_Charge_ID=? AND C_AcctSchema_ID=?";
			else
				sql = "SELECT CH_Revenue_Acct FROM C_Charge_Acct WHERE C_Charge_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Charge_ID();
		}
		else if (acctType == ACCTTYPE_V_Liability)
		{
			sql = "SELECT V_Liability_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_V_Liability_Services)
		{
			sql = "SELECT V_Liability_Services_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_C_Receivable)
		{
			sql = "SELECT C_Receivable_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_C_Receivable_Services)
		{
			sql = "SELECT C_Receivable_Services_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_V_Prepayment)
		{
			sql = "SELECT V_Prepayment_Acct FROM C_BP_Vendor_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_C_Prepayment)
		{
			sql = "SELECT C_Prepayment_Acct FROM C_BP_Customer_Acct WHERE C_BPartner_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Account Type - Payment  */
		else if (acctType == ACCTTYPE_UnallocatedCash)
		{
			sql = "SELECT B_UnallocatedCash_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (acctType == ACCTTYPE_BankInTransit)
		{
			sql = "SELECT B_InTransit_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if(acctType == ACCTTYPE_BankUnidentified) {
			sql = "SELECT B_Unidentified_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (acctType == ACCTTYPE_PaymentSelect)
		{
			sql = "SELECT B_PaymentSelect_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		
		/** Account Type - Allocation   */
		else if (acctType == ACCTTYPE_DiscountExp)
		{
			sql = "SELECT a.PayDiscount_Exp_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_DiscountRev)
		{
			sql = "SELECT PayDiscount_Rev_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}
		else if (acctType == ACCTTYPE_WriteOff)
		{
			sql = "SELECT WriteOff_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Account Type - Bank Statement   */
		else if (acctType == ACCTTYPE_BankAsset)
		{
			sql = "SELECT B_Asset_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (acctType == ACCTTYPE_InterestRev)
		{
			sql = "SELECT B_InterestRev_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}
		else if (acctType == ACCTTYPE_InterestExp)
		{
			sql = "SELECT B_InterestExp_Acct FROM C_BankAccount_Acct WHERE C_BankAccount_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_BankAccount_ID();
		}

		/** Account Type - Cash     */
		else if (acctType == ACCTTYPE_CashAsset)
		{
			sql = "SELECT CB_Asset_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (acctType == ACCTTYPE_CashTransfer)
		{
			sql = "SELECT CB_CashTransfer_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (acctType == ACCTTYPE_CashExpense)
		{
			sql = "SELECT CB_Expense_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (acctType == ACCTTYPE_CashReceipt)
		{
			sql = "SELECT CB_Receipt_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}
		else if (acctType == ACCTTYPE_CashDifference)
		{
			sql = "SELECT CB_Differences_Acct FROM C_CashBook_Acct WHERE C_CashBook_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_CashBook_ID();
		}

		/** Inventory Accounts          */
		else if (acctType == ACCTTYPE_InvDifferences)
		{
			sql = "SELECT W_Differences_Acct FROM M_Warehouse_Acct WHERE M_Warehouse_ID=? AND C_AcctSchema_ID=?";
			//  "SELECT W_Inventory_Acct, W_Revaluation_Acct, W_InvActualAdjust_Acct FROM M_Warehouse_Acct WHERE M_Warehouse_ID=? AND C_AcctSchema_ID=?";
			para_1 = getM_Warehouse_ID();
		}
		else if (acctType == ACCTTYPE_NotInvoicedReceipts)
		{
			sql = "SELECT NotInvoicedReceipts_Acct FROM C_BP_Group_Acct a, C_BPartner bp "
				+ "WHERE a.C_BP_Group_ID=bp.C_BP_Group_ID AND bp.C_BPartner_ID=? AND a.C_AcctSchema_ID=?";
			para_1 = getC_BPartner_ID();
		}

		/** Project Accounts          	*/
		else if (acctType == ACCTTYPE_ProjectAsset)
		{
			sql = "SELECT PJ_Asset_Acct FROM C_Project_Acct WHERE C_Project_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Project_ID();
		}
		else if (acctType == ACCTTYPE_ProjectWIP)
		{
			sql = "SELECT PJ_WIP_Acct FROM C_Project_Acct WHERE C_Project_ID=? AND C_AcctSchema_ID=?";
			para_1 = getC_Project_ID();
		}

		/** GL Accounts                 */
		else if (acctType == ACCTTYPE_PPVOffset)
		{
			sql = "SELECT PPVOffset_Acct FROM C_AcctSchema_GL WHERE C_AcctSchema_ID=?";
			para_1 = -1;
		}
		else if (acctType == ACCTTYPE_CommitmentOffset)
		{
			sql = "SELECT CommitmentOffset_Acct FROM C_AcctSchema_GL WHERE C_AcctSchema_ID=?";
			para_1 = -1;
		}
		else if (acctType == ACCTTYPE_CommitmentOffsetSales)
		{
			sql = "SELECT CommitmentOffsetSales_Acct FROM C_AcctSchema_GL WHERE C_AcctSchema_ID=?";
			para_1 = -1;
		}

		else
		{
			log.severe ("Not found AcctType=" + acctType);
			return 0;
		}
		//  Do we have sql & Parameter
		if (sql == null || para_1 == 0)
		{
			log.severe ("No Parameter for AcctType=" + acctType + " - SQL=" + sql);
			return 0;
		}

		//  Get Acct
		int Account_ID = 0;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			if (para_1 == -1)   //  GL Accounts
				pstmt.setInt (1, acctSchema.getC_AcctSchema_ID());
			else
			{
				pstmt.setInt (1, para_1);
				pstmt.setInt (2, acctSchema.getC_AcctSchema_ID());
			}
			rs = pstmt.executeQuery();
			if (rs.next())
				Account_ID = rs.getInt(1);
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, "AcctType=" + acctType + " - SQL=" + sql, e);
			return 0;
		}
		finally {
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		//	No account
		if (Account_ID == 0)
		{
			log.severe ("NO account Type="
				+ acctType + ", Record=" + p_po.get_ID());
			return 0;
		}
		return Account_ID;
	}	//	getAccount_ID

	/**
	 *	Get the account for Accounting Schema
	 *  @param acctType see ACCTTYPE_*
	 *  @param acctSchema accounting schema
	 *  @return Account
	 */
	public final MAccount getAccount (int acctType, MAcctSchema acctSchema)
	{
		int validCombinationId = getValidCombinationId(acctType, acctSchema);
		if (validCombinationId == 0)
			return null;
		//	Return Account
		MAccount account = MAccount.getValidCombination (acctSchema.getCtx(), validCombinationId ,getTrxName());
		return account;
	}	//	getAccount

	
	/**************************************************************************
	 *  Save to Disk - set posted flag
	 *  @param trxName transaction name
	 *  @return true if saved
	 */
	private final boolean save (String trxName)
	{
		final String postStatus = p_Status;
        log.fine(toString() + "->" + postStatus);

		int no = setPostedStatusAndUnlock(get_TableName(), p_po.get_ID(), 
		        postStatus, trxName);
		return no == 1;
	}   //  save

    private static final int setPostedStatusAndUnlock(String tableName, int record_id, 
            String postStatus, String trxName) {

        StringBuilder sql = new StringBuilder("UPDATE ");
		sql.append(tableName).append(" SET Posted='").append(postStatus)
			.append("',Processing='N' ")
			.append("WHERE ")
			.append(tableName).append("_ID=").append(record_id);
		return DB.executeUpdate(sql.toString(), trxName);

    }

	/**
	 *  Get DocLine with ID
	 *  @param Record_ID Record ID
	 *  @return DocLine
	 */
	public DocLine getDocLine (int Record_ID)
	{
		if (p_lines == null || p_lines.length == 0 || Record_ID == 0)
			return null;

		for (int i = 0; i < p_lines.length; i++)
		{
			if (p_lines[i].get_ID() == Record_ID)
				return p_lines[i];
		}
		return null;
	}   //  getDocLine

	/**
	 *  String Representation
	 *  @return String
	 */
	public String toString()
	{
		return p_po.toString();
	}   //  toString

	
	/**
	 * 	Get AD_Client_ID
	 *	@return client
	 */
	public int getAD_Client_ID()
	{
		return p_po.getAD_Client_ID();
	}	//	getAD_Client_ID
	
	/**
	 * 	Get AD_Org_ID
	 *	@return org
	 */
	public int getAD_Org_ID()
	{
		return p_po.getAD_Org_ID();
	}	//	getAD_Org_ID

	/**
	 * 	Get Document No
	 *	@return document No
	 */
	public String getDocumentNo()
	{
		if (m_DocumentNo != null)
			return m_DocumentNo;
		int index = p_po.get_ColumnIndex("DocumentNo");
		if (index == -1)
			index = p_po.get_ColumnIndex("Name");
		if (index == -1)
			throw new UnsupportedOperationException("No DocumentNo");
		m_DocumentNo = (String)p_po.get_Value(index);
		return m_DocumentNo;
	}	//	getDocumentNo
	
	/**
	 * 	Get Description
	 *	@return Description
	 */
	public String getDescription()
	{
		if (m_Description == null)
		{
			int index = p_po.get_ColumnIndex("Description");
			if (index != -1)
				m_Description = (String)p_po.get_Value(index);
			else
				m_Description = "";
		}
		return m_Description;
	}	//	getDescription
	
	/**
	 * 	Get C_Currency_ID
	 *	@return currency
	 */
	public int getC_Currency_ID()
	{
		if (m_C_Currency_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_Currency_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_Currency_ID = ii.intValue();
			}
			if (m_C_Currency_ID == -1)
				m_C_Currency_ID = NO_CURRENCY;
		}
		return m_C_Currency_ID;
	}	//	getC_Currency_ID
	
	/**
	 * 	Set C_Currency_ID
	 *	@param C_Currency_ID id
	 */
	public void setC_Currency_ID (int C_Currency_ID)
	{
		m_C_Currency_ID = C_Currency_ID;
	}	//	setC_Currency_ID
	
	/**
	 * 	Is Multi Currency
	 *	@return mc
	 */
	public boolean isMultiCurrency()
	{
		return m_MultiCurrency;
	}	//	isMultiCurrency

	/**
	 * 	Set Multi Currency
	 *	@param mc multi currency
	 */
	public void setIsMultiCurrency (boolean mc)
	{
		m_MultiCurrency = mc;
	}	//	setIsMultiCurrency
	
	/**
	 * 	Is Tax Included
	 *	@return tax incl
	 */
	public boolean isTaxIncluded()
	{
		return m_TaxIncluded;
	}	//	isTaxIncluded

	/**
	 * 	Set Tax Included
	 *	@param ti Tax Included
	 */
	public void setIsTaxIncluded (boolean ti)
	{
		m_TaxIncluded = ti;
	}	//	setIsTaxIncluded
	
	/**
	 * 	Get C_ConversionType_ID
	 *	@return ConversionType
	 */
	public int getC_ConversionType_ID()
	{
		int index = p_po.get_ColumnIndex("C_ConversionType_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_ConversionType_ID
	
	/**
	 * 	Get GL_Category_ID
	 *	@return category
	 */
	public int getGL_Category_ID()
	{
		return m_GL_Category_ID;
	}	//	getGL_Category_ID
	
	/**
	 * 	Get GL_Category_ID
	 *	@return category
	 */
	public int getGL_Budget_ID()
	{
		int index = p_po.get_ColumnIndex("GL_Budget_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getGL_Budget_ID

	/**
	 * 	Get Accounting Date
	 *	@return currency
	 */
	public Timestamp getDateAcct()
	{
		if (m_DateAcct != null)
			return m_DateAcct;
		int index = p_po.get_ColumnIndex("DateAcct");
		if (index != -1)
		{
			m_DateAcct = (Timestamp)p_po.get_Value(index);
			if (m_DateAcct != null)
				return m_DateAcct;
		}
		throw new IllegalStateException("No DateAcct");
	}	//	getDateAcct

	/**
	 * 	Set Date Acct
	 *	@param da accounting date
	 */
	public void setDateAcct (Timestamp da)
	{
		m_DateAcct = da;
	}	//	setDateAcct
	
	/**
	 * 	Get Document Date
	 *	@return currency
	 */
	public Timestamp getDateDoc()
	{
		if (m_DateDoc != null)
			return m_DateDoc;
		int index = p_po.get_ColumnIndex("DateDoc");
		if (index == -1)
			index = p_po.get_ColumnIndex("MovementDate");
		if (index != -1)
		{
			m_DateDoc = (Timestamp)p_po.get_Value(index);
			if (m_DateDoc != null)
				return m_DateDoc;
		}
		throw new IllegalStateException("No DateDoc");
	}	//	getDateDoc
	
	/**
	 * 	Set Date Doc
	 *	@param dd document date
	 */
	public void setDateDoc (Timestamp dd)
	{
		m_DateDoc = dd;
	}	//	setDateDoc

	/**
	 * 	Is Document Posted
	 *	@return true if posted
	 */
	public boolean isPosted()
	{
		int index = p_po.get_ColumnIndex("Posted");
		if (index != -1)
		{
			Object posted = p_po.get_Value(index);
			if (posted instanceof Boolean)
				return ((Boolean)posted).booleanValue();
			if (posted instanceof String)
				return "Y".equals(posted);
		}
		throw new IllegalStateException("No Posted");
	}	//	isPosted
	
	/**
	 * 	Is Sales Trx
	 *	@return true if posted
	 */
	public boolean isSOTrx()
	{
		int index = p_po.get_ColumnIndex("IsSOTrx");
		if (index == -1)
			index = p_po.get_ColumnIndex("IsReceipt");
		if (index != -1)
		{
			Object posted = p_po.get_Value(index);
			if (posted instanceof Boolean)
				return ((Boolean)posted).booleanValue();
			if (posted instanceof String)
				return "Y".equals(posted);
		}
		return false;
	}	//	isSOTrx

	/**
	 * 	Get C_DocType_ID
	 *	@return DocType
	 */
	public int getC_DocType_ID()
	{
		int index = p_po.get_ColumnIndex("C_DocType_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			//	DocType does not exist - get DocTypeTarget
			if (ii != null && ii.intValue() == 0)
			{
				index = p_po.get_ColumnIndex("C_DocTypeTarget_ID");
				if (index != -1)
					ii = (Integer)p_po.get_Value(index);
			}
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_DocType_ID
	
	/**
	 * 	Get header level C_Charge_ID
	 *	@return Charge
	 */
	public int getC_Charge_ID()
	{
		int index = p_po.get_ColumnIndex("C_Charge_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Charge_ID

	/**
	 * 	Get SalesRep_ID
	 *	@return SalesRep
	 */
	public int getSalesRep_ID()
	{
		int index = p_po.get_ColumnIndex("SalesRep_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getSalesRep_ID
	
	/**
	 * 	Get C_BankAccount_ID
	 *	@return BankAccount
	 */
	public int getC_BankAccount_ID()
	{
		if (m_C_BankAccount_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_BankAccount_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_BankAccount_ID = ii.intValue();
			}
			if (m_C_BankAccount_ID == -1)
				m_C_BankAccount_ID = 0;
		}
		return m_C_BankAccount_ID;
	}	//	getC_BankAccount_ID

	/**
	 * 	Set C_BankAccount_ID
	 *	@param C_BankAccount_ID bank acct
	 */
	public void setC_BankAccount_ID (int C_BankAccount_ID)
	{
		m_C_BankAccount_ID = C_BankAccount_ID;
	}	//	setC_BankAccount_ID
		
	/**
	 * 	Get C_CashBook_ID
	 *	@return CashBook
	 */
	public int getC_CashBook_ID()
	{
		if (m_C_CashBook_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_CashBook_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_CashBook_ID = ii.intValue();
			}
			if (m_C_CashBook_ID == -1)
				m_C_CashBook_ID = 0;
		}
		return m_C_CashBook_ID;
	}	//	getC_CashBook_ID

	/**
	 * 	Set C_CashBook_ID
	 *	@param C_CashBook_ID cash book
	 */
	public void setC_CashBook_ID (int C_CashBook_ID)
	{
		m_C_CashBook_ID = C_CashBook_ID;
	}	//	setC_CashBook_ID

	/**
	 * 	Get M_Warehouse_ID
	 *	@return Warehouse
	 */
	public int getM_Warehouse_ID()
	{
		int index = p_po.get_ColumnIndex("M_Warehouse_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getM_Warehouse_ID

	
	/**
	 * 	Get C_BPartner_ID
	 *	@return BPartner
	 */
	public int getC_BPartner_ID()
	{
		if (m_C_BPartner_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_BPartner_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_C_BPartner_ID = ii.intValue();
			}
			if (m_C_BPartner_ID == -1)
				m_C_BPartner_ID = 0;
		}
		return m_C_BPartner_ID;
	}	//	getC_BPartner_ID

	/**
	 * 	Set C_BPartner_ID
	 *	@param C_BPartner_ID bp
	 */
	public void setC_BPartner_ID (int C_BPartner_ID)
	{
		m_C_BPartner_ID = C_BPartner_ID;
	}	//	setC_BPartner_ID
	
	/**
	 * 	Get C_BPartner_Location_ID
	 *	@return BPartner Location
	 */
	public int getC_BPartner_Location_ID()
	{
		int index = p_po.get_ColumnIndex("C_BPartner_Location_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_BPartner_Location_ID

	/**
	 * 	Get C_Project_ID
	 *	@return Project
	 */
	public int getC_Project_ID()
	{
		int index = p_po.get_ColumnIndex("C_Project_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Project_ID
	
	/**
	 * 	Get C_ProjectPhase_ID
	 *	@return Project Phase
	 */
	public int getC_ProjectPhase_ID()
	{
		int index = p_po.get_ColumnIndex("C_ProjectPhase_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_ProjectPhase_ID
	
	/**
	 * 	Get C_ProjectTask_ID
	 *	@return Project Task
	 */
	public int getC_ProjectTask_ID()
	{
		int index = p_po.get_ColumnIndex("C_ProjectTask_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_ProjectTask_ID
	
	/**
	 * 	Get C_SalesRegion_ID
	 *	@return Sales Region
	 */
	public int getC_SalesRegion_ID()
	{
		int index = p_po.get_ColumnIndex("C_SalesRegion_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_SalesRegion_ID
	
	/**
	 * 	Get C_SalesRegion_ID
	 *	@return Sales Region
	 */
	public int getBP_C_SalesRegion_ID()
	{
		if (m_BP_C_SalesRegion_ID == -1)
		{
			int index = p_po.get_ColumnIndex("C_SalesRegion_ID");
			if (index != -1)
			{
				Integer ii = (Integer)p_po.get_Value(index);
				if (ii != null)
					m_BP_C_SalesRegion_ID = ii.intValue();
			}
			if (m_BP_C_SalesRegion_ID == -1)
				m_BP_C_SalesRegion_ID = 0;
		}
		return m_BP_C_SalesRegion_ID;
	}	//	getBP_C_SalesRegion_ID

	/**
	 * 	Set C_SalesRegion_ID
	 *	@param C_SalesRegion_ID id
	 */
	public void setBP_C_SalesRegion_ID (int C_SalesRegion_ID)
	{
		m_BP_C_SalesRegion_ID = C_SalesRegion_ID;
	}	//	setBP_C_SalesRegion_ID
	
	/**
	 * 	Get C_Activity_ID
	 *	@return Activity
	 */
	public int getC_Activity_ID()
	{
		int index = p_po.get_ColumnIndex("C_Activity_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Activity_ID

	/**
	 * 	Get C_Campaign_ID
	 *	@return Campaign
	 */
	public int getC_Campaign_ID()
	{
		int index = p_po.get_ColumnIndex("C_Campaign_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getC_Campaign_ID

	/**
	 * 	Get M_Product_ID
	 *	@return Product
	 */
	public int getM_Product_ID()
	{
		int index = p_po.get_ColumnIndex("M_Product_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getM_Product_ID

	/**
	 * 	Get AD_OrgTrx_ID
	 *	@return Trx Org
	 */
	public int getAD_OrgTrx_ID()
	{
		int index = p_po.get_ColumnIndex("AD_OrgTrx_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getAD_OrgTrx_ID

	/**
	 * 	Get C_LocFrom_ID
	 *	@return loc from
	 */
	public int getC_LocFrom_ID()
	{
		return m_C_LocFrom_ID;
	}	//	getC_LocFrom_ID
	
	/**
	 * 	Set C_LocFrom_ID
	 *	@param C_LocFrom_ID loc from
	 */
	public void setC_LocFrom_ID(int C_LocFrom_ID)
	{
		m_C_LocFrom_ID = C_LocFrom_ID;
	}	//	setC_LocFrom_ID

	/**
	 * 	Get C_LocTo_ID
	 *	@return loc to
	 */
	public int getC_LocTo_ID()
	{
		return m_C_LocTo_ID;
	}	//	getC_LocTo_ID

	/**
	 * 	Set C_LocTo_ID
	 *	@param C_LocTo_ID loc to
	 */
	public void setC_LocTo_ID(int C_LocTo_ID)
	{
		m_C_LocTo_ID = C_LocTo_ID;
	}	//	setC_LocTo_ID

	/**
	 * 	Get User1_ID
	 *	@return Campaign
	 */
	public int getUser1_ID()
	{
		int index = p_po.get_ColumnIndex("User1_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser1_ID
	
	/**
	 * 	Get User2_ID
	 *	@return User2_ID
	 */
	public int getUser2_ID()
	{
		int index = p_po.get_ColumnIndex("User2_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser2_ID

	/**
	 * 	Get User3_ID
	 *	@return User3_ID
	 */
	public int getUser3_ID()
	{
		int index = p_po.get_ColumnIndex("User3_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser3_ID

	/**
	 * 	Get User4_ID
	 *	@return User4_ID
	 */
	public int getUser4_ID()
	{
		int index = p_po.get_ColumnIndex("User4_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getUser4_ID

	/**
	 *  Get UserElement 1
	 *  @return user Element defined 1
	 */
	public int getUserElement1_ID()
	{
		int index = p_po.get_ColumnIndex("UserElement1_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}   //  getUserElement1_ID

	/**
	 *  Get UserElement 1
	 *  @return user Element defined 1
	 */
	public int getUserElement2_ID()
	{
		int index = p_po.get_ColumnIndex("UserElement2_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}   //  getUserElement1_ID

        	/**
	 * 	Get User Defined value
	 *	@return User defined
	 */
	public int getValue (String ColumnName)
	{
		int index = p_po.get_ColumnIndex(ColumnName);
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}	//	getValue
	
	
	/*************************************************************************/
	//  To be overwritten by Subclasses

	/**
	 *  Load Document Details
	 *  @return error message or null
	 */
	protected abstract String loadDocumentDetails ();

	/**
	 *  Get Source Currency Balance - subtracts line (and tax) amounts from total - no rounding
	 *  @return positive amount, if total header is bigger than lines
	 */
	public abstract BigDecimal getBalance();

	/**
	 *  Create Facts (the accounting logic)
	 *  @param as accounting schema
	 *  @return Facts
	 */
	public abstract ArrayList<Fact> createFacts (MAcctSchema as);

	/**
	 *  Get Facts (the accounting logic)
	 *  @return Facts
	 */
	public ArrayList<Fact> getFacts() {
		return m_fact;
	}

	/*
	 * Array of tables with Post column
	 */
	public static int[] getDocumentsTableID() {
		fillDocumentsTableArrays();
		return documentsTableID;
	}

	public static String[] getDocumentsTableName() {
		fillDocumentsTableArrays();
		return documentsTableName;
	}

	private static void fillDocumentsTableArrays() {
		if (documentsTableID == null) {
			String sql = "SELECT t.AD_Table_ID, t.TableName " +
					"FROM AD_Table t, AD_Column c " +
					"WHERE t.AD_Table_ID=c.AD_Table_ID AND " +
					"c.ColumnName='Posted' AND " +
					"IsView='N' " +
					"ORDER BY t.AD_Table_ID";
			ArrayList<Integer> tableIDs = new ArrayList<Integer>();
			ArrayList<String> tableNames = new ArrayList<String>();
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try
			{
				pstmt = DB.prepareStatement(sql, null);
				rs = pstmt.executeQuery();
				while (rs.next())
				{
					tableIDs.add(rs.getInt(1));
					tableNames.add(rs.getString(2));
				}
			}
			catch (SQLException e)
			{
				throw new DBException(e, sql);
			}
			finally
			{
				DB.close(rs, pstmt);
				rs = null; pstmt = null;
			}
			//	Convert to array
			documentsTableID = new int[tableIDs.size()];
			documentsTableName = new String[tableIDs.size()];
			for (int i = 0; i < documentsTableID.length; i++)
			{
				documentsTableID[i] = tableIDs.get(i);
				documentsTableName[i] = tableNames.get(i);
			}
		}
	}

	/**
	 * get Document Status
	 * @return
	 */
	public String getDocStatus()
	{
		if (m_DocStatus != null)
			return m_DocStatus;

		//	DocStatus
		int index = p_po.get_ColumnIndex("DocStatus");
		if (index != -1)
			m_DocStatus = (String)p_po.get_Value(index);

		return m_DocStatus;
	}

	/** get Reversal Id for this Document
	 * @return
	 */
	public int getReversalId()
	{

		int index = p_po.get_ColumnIndex("Reversal_ID");
		if (index != -1)
		{
			Integer ii = (Integer)p_po.get_Value(index);
			if (ii != null)
				return ii.intValue();
		}
		return 0;
	}

	/**
	 * Return true if document is reverted
	 * @return
	 */
	public Boolean isReversed()
	{
		if (getReversalId() > 0)
			return true;
		else
			return false;
	}

	/**
	 * Return true if the document is the reverse generated
	 * @return
	 */
	public Boolean IsReverseGenerated()
	{
		return getReversalId() < getPO().get_ID();
	}

	/**
	 * Return value from Document Type
	 * @return
	 */
	public Boolean isReverseWithOriginalAccounting()
	{
		String isReverseWithOriginalAccounting = DB.getSQLValueString(getPO().get_TrxName() , "SELECT IsReversedWithOriginalAcct FROM C_DocType WHERE C_DocType_ID=?", getC_DocType_ID());
		if (isReverseWithOriginalAccounting != null && "Y".equals(isReverseWithOriginalAccounting))
			return true;
		else
			return false;
	}
	
	/**
	 * Generate Reverse using Orginal Accounting
	 * @param originalAccountSchema
	 * @return
	 */
	private String generateReverseWithOriginalAccounting(MAcctSchema originalAccountSchema) {
		getReversalFactAcct(originalAccountSchema).stream().forEach(factAcct -> {
			MFactAcct reverseFactAcct = new MFactAcct(getPO().getCtx() , 0 , getPO().get_TrxName());
			PO.copyValues(factAcct, reverseFactAcct);
			reverseFactAcct.setAD_Org_ID(factAcct.getAD_Org_ID());
			reverseFactAcct.setAD_Table_ID(getPO().get_Table_ID());
			reverseFactAcct.setDateAcct(getDateAcct());
			reverseFactAcct.setC_Period_ID(getC_Period_ID());
			reverseFactAcct.setRecord_ID(getPO().get_ID());
			reverseFactAcct.setQty(factAcct.getQty().negate());
			FactLine.setSourceAmount(originalAccountSchema, reverseFactAcct, factAcct.getAmtSourceDr().negate(), factAcct.getAmtSourceCr().negate());
			FactLine.setAccountingAmount(originalAccountSchema, reverseFactAcct, factAcct.getAmtAcctDr().negate(), factAcct.getAmtAcctCr().negate());
			reverseFactAcct.saveEx();
		});
		return STATUS_Posted;
	}

	/**
	 * get Reversal Fact Accounts
	 * @param originalAccountSchema
	 * @return
	 */
	private List<MFactAcct> getReversalFactAcct(MAcctSchema originalAccountSchema)
	{
		StringBuilder whereClause = new StringBuilder();
		whereClause.append(MFactAcct.COLUMNNAME_AD_Table_ID).append("=? AND ");
		whereClause.append(MFactAcct.COLUMNNAME_Record_ID).append("=? AND ");
		whereClause.append(MFactAcct.COLUMNNAME_C_AcctSchema_ID).append("=?");
		return new Query(getCtx(), MFactAcct.Table_Name , whereClause.toString() , getPO().get_TrxName())
				.setClient_ID()
				.setParameters(getPO().get_Table_ID(), getReversalId(), originalAccountSchema.getC_AcctSchema_ID())
				.setOrderBy(MFactAcct.COLUMNNAME_Fact_Acct_ID)
				.list();
	}
	
	/**
	 * This method should return the columnName used for the accounting date.  
	 * In most documents this is DateAcct but where documents use another 
	 * column, they should implement this same method to return that column 
	 * name.
	 *  
	 * @return the column name used for the accounting date.
	 */
	public static String getDateAcctColumnName() {
	
	    return "DateAcct";
	    
	}
	
	/**
	 * Return the columnName used for the accounting date for the 
	 * given table name
	 * @param tableName
	 * @return the table columnName or null if not found
	 */
    public static String getDateAcctColumnName(String tableName) {

        Class<?> cClass = getDocClass(tableName);
        Method getDateAcctColumnNameMethod =
                getDateAcctColumnNameMethod(cClass);
        return getDateAcctColumnName(cClass,
                getDateAcctColumnNameMethod);

    }

    private static Class<?> getDocClass(String tableName) {
    
        if(Util.isEmpty(tableName))
            return null;
        
        String className = null;
        String packageName = "org.compiere.acct";
        int firstUnderscore = tableName.indexOf("_");
        if (firstUnderscore == 1)
            className = packageName + ".Doc_"
                    + tableName.substring(2).replace("_", "");
        else
            className = packageName + ".Doc_"
                    + tableName.replace("_", "");

        Class<?> cClass = null;
        try {
            cClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            s_log.config("Unrecognized classname for Document:  "
                    + className);
        }
        return cClass;
    
    }

    private static Method getDateAcctColumnNameMethod(Class<?> cClass) {
    
        Method getDateAcctColumnName = null;
        if (cClass != null) {
            try {
                getDateAcctColumnName =
                        cClass.getMethod("getDateAcctColumnName");
            } catch (NoSuchMethodException | SecurityException e) {
                s_log.config("Unable to call "
                        + "getDateAcctColumnName for Document "
                        + cClass.getCanonicalName());
            }
        }
        return getDateAcctColumnName;
    
    }

    private static String getDateAcctColumnName(Class<?> cClass,
            Method getDateAcctColumnNameMethod) {

        String dateAcctColumnName = null;
        if (cClass != null) {
            try {
                dateAcctColumnName =
                        (String) getDateAcctColumnNameMethod.invoke(null);
            } catch (IllegalAccessException
                    | IllegalArgumentException
                    | InvocationTargetException e) {
                s_log.config("Unable to invoke "
                        + "getDateAcctColumnName for Document "
                        + cClass.getCanonicalName());
            }
        }
        return dateAcctColumnName;

    }

}   //  Doc
