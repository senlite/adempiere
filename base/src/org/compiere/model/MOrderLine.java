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
package org.compiere.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.engine.IDocumentLine;
import org.adempiere.exceptions.ProductNotOnPriceListException;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

/**
 *  Order Line Model.
 * 	<code>
 * 			MOrderLine ol = new MOrderLine(m_order);
			ol.setM_Product_ID(wbl.getM_Product_ID());
			ol.setQtyOrdered(wbl.getQuantity());
			ol.setPrice();
			ol.setPriceActual(wbl.getPrice());
			ol.setTax();
			ol.saveEx();

 *	</code>
 *  @author Jorg Janke
 *  @version $Id: MOrderLine.java,v 1.6 2006/10/02 05:18:39 jjanke Exp $
 * 
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 *			<li>BF [ 2588043 ] Insufficient message ProductNotOnPriceList
 * @author Michael Judd, www.akunagroup.com
 * 			<li>BF [ 1733602 ] Price List including Tax Error - when a user changes the orderline or
 * 				invoice line for a product on a price list that includes tax, the net amount is
 * 				incorrectly calculated.
 */
public class MOrderLine extends X_C_OrderLine implements IDocumentLine
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 7305265800857547603L;

	/**
	 * 	Get Order Unreserved Qty
	 *	@param ctx context
	 *	@param M_Warehouse_ID wh
	 *	@param M_Product_ID product
	 *	@param M_AttributeSetInstance_ID asi
	 *	@param excludeC_OrderLine_ID exclude C_OrderLine_ID
	 *	@return Unreserved Qty
	 */
	public static BigDecimal getNotReserved (Properties ctx, int M_Warehouse_ID, 
		int M_Product_ID, int M_AttributeSetInstance_ID, int excludeC_OrderLine_ID)
	{
		BigDecimal retValue = Env.ZERO;
		String sql = "SELECT SUM(QtyOrdered-QtyDelivered-QtyReserved) "
			+ "FROM C_OrderLine ol"
			+ " INNER JOIN C_Order o ON (ol.C_Order_ID=o.C_Order_ID) "
			+ "WHERE ol.M_Warehouse_ID=?"	//	#1
			+ " AND M_Product_ID=?"			//	#2
			+ " AND o.IsSOTrx='Y' AND o.DocStatus='DR'"
			+ " AND QtyOrdered-QtyDelivered-QtyReserved<>0"
			+ " AND ol.C_OrderLine_ID<>?";
		if (M_AttributeSetInstance_ID != 0)
			sql += " AND M_AttributeSetInstance_ID=?";
		
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql, null);
			pstmt.setInt (1, M_Warehouse_ID);
			pstmt.setInt (2, M_Product_ID);
			pstmt.setInt (3, excludeC_OrderLine_ID);
			if (M_AttributeSetInstance_ID != 0)
				pstmt.setInt (4, M_AttributeSetInstance_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
				retValue = rs.getBigDecimal(1);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log (Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		if (retValue == null)
			s_log.fine("-");
		else
			s_log.fine(retValue.toString());
		return retValue;
	}	//	getNotReserved
	
	
	/**	Logger	*/
	private static CLogger s_log = CLogger.getCLogger (MOrderLine.class);
	
	/**************************************************************************
	 *  Default Constructor
	 *  @param ctx context
	 *  @param  C_OrderLine_ID  order line to load
	 *  @param trxName trx name
	 */
	public MOrderLine (Properties ctx, int C_OrderLine_ID, String trxName)
	{
		super (ctx, C_OrderLine_ID, trxName);
		if (C_OrderLine_ID == 0)
		{
		//	setC_Order_ID (0);
		//	setLine (0);
		//	setM_Warehouse_ID (0);	// @M_Warehouse_ID@
		//	setC_BPartner_ID(0);
		//	setC_BPartner_Location_ID (0);	// @C_BPartner_Location_ID@
		//	setC_Currency_ID (0);	// @C_Currency_ID@
		//	setDateOrdered (new Timestamp(System.currentTimeMillis()));	// @DateOrdered@
			//
		//	setC_Tax_ID (0);
		//	setC_UOM_ID (0);
			//
			setFreightAmt (Env.ZERO);
			setLineNetAmt (Env.ZERO);
			//
			setPriceEntered(Env.ZERO);
			setPriceActual (Env.ZERO);
			setPriceLimit (Env.ZERO);
			setPriceList (Env.ZERO);
			//
			setM_AttributeSetInstance_ID(0);
			//
			setQtyEntered (Env.ZERO);
			setQtyOrdered (Env.ZERO);	// 1
			setQtyDelivered (Env.ZERO);
			setQtyInvoiced (Env.ZERO);
			setQtyReserved (Env.ZERO);
			//
			setIsDescription (false);	// N
			setProcessed (false);
			setLine (0);
		}
	}	//	MOrderLine
	
	/**
	 *  Parent Constructor.
	 		ol.setM_Product_ID(wbl.getM_Product_ID());
			ol.setQtyOrdered(wbl.getQuantity());
			ol.setPrice();
			ol.setPriceActual(wbl.getPrice());
			ol.setTax();
			ol.saveEx();
	 *  @param  order parent order
	 */
	public MOrderLine (MOrder order)
	{
		this (order.getCtx(), 0, order.get_TrxName());
		if (order.get_ID() == 0)
			throw new IllegalArgumentException("Header not saved");
		setC_Order_ID (order.getC_Order_ID());	//	parent
		setOrder(order);
	}	//	MOrderLine

	/**
	 *  Load Constructor
	 *  @param ctx context
	 *  @param rs result set record
	 *  @param trxName transaction
	 */
	public MOrderLine (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MOrderLine

	private int 			m_M_PriceList_ID = 0;
	//
	private boolean			m_IsSOTrx = true;
	//	Product Pricing
	private MProductPricing	m_productPrice = null;

	/** Tax							*/
	private MTax 		m_tax = null;
	
	/** Cached Currency Precision	*/
	private Integer			m_precision = null;
	/**	Product					*/
	private MProduct 		m_product = null;
	/**	Charge					*/
	private MCharge 		m_charge = null;
	/** Parent					*/
	private MOrder			m_parent = null;
	
	/**
	 * 	Set Defaults from Order.
	 * 	Does not set Parent !!
	 * 	@param order order
	 */
	public void setOrder (MOrder order)
	{
		setClientOrg(order);
		setC_BPartner_ID(order.getC_BPartner_ID());
		setC_BPartner_Location_ID(order.getC_BPartner_Location_ID());
		setM_Warehouse_ID(order.getM_Warehouse_ID());
		setDateOrdered(order.getDateOrdered());
		setDatePromised(order.getDatePromised());
		setC_Currency_ID(order.getC_Currency_ID());
		//
		setHeaderInfo(order);	//	sets m_order
		//	Don't set Activity, etc as they are overwrites
	}	//	setOrder

	/**
	 * 	Set Header Info
	 *	@param order order
	 */
	public void setHeaderInfo (MOrder order)
	{
		m_parent = order;
		m_precision = new Integer(order.getPrecision());
		m_M_PriceList_ID = order.getM_PriceList_ID();
		m_IsSOTrx = order.isSOTrx();
	}	//	setHeaderInfo
	
	/**
	 * 	Get Parent
	 *	@return parent
	 */
	public MOrder getParent()
	{
		if (m_parent == null)
			m_parent = new MOrder(getCtx(), getC_Order_ID(), get_TrxName());
		return m_parent;
	}	//	getParent
	
	/**
	 * 	Set Price Entered/Actual.
	 * 	Use this Method if the Line UOM is the Product UOM 
	 *	@param PriceActual price
	 */
	public void setPrice (BigDecimal PriceActual)
	{
		setPriceEntered(PriceActual);
		setPriceActual (PriceActual);
	}	//	setPrice

	/**
	 * 	Set Price Actual.
	 * 	(actual price is not updateable)
	 *	@param PriceActual actual price
	 */
	public void setPriceActual (BigDecimal PriceActual)
	{
		if (PriceActual == null) 
			throw new IllegalArgumentException ("PriceActual is mandatory");
		set_ValueNoCheck("PriceActual", PriceActual);
	}	//	setPriceActual

	/**
	 * 	Set Price for Product and PriceList.
	 * 	Use only if newly created.
	 * 	Uses standard price list of not set by order constructor
	 */
	public void setPrice()
	{
		if (getM_Product_ID() == 0)
			return;
		if (m_M_PriceList_ID == 0)
			throw new IllegalStateException("PriceList unknown!");
		setPrice (m_M_PriceList_ID);
	}	//	setPrice

	/**
	 * 	Set Price for Product and PriceList
	 * 	@param M_PriceList_ID price list
	 */
	public void setPrice (int M_PriceList_ID)
	{
		if (getM_Product_ID() == 0)
			return;
		//
		log.fine(toString() + " - M_PriceList_ID=" + M_PriceList_ID);
		getProductPricing (M_PriceList_ID);
		setPriceActual (m_productPrice.getPriceStd());
		setPriceList (m_productPrice.getPriceList());
		setPriceLimit (m_productPrice.getPriceLimit());
		//
		if (getQtyEntered().compareTo(getQtyOrdered()) == 0)
			setPriceEntered(getPriceActual());
		else
			setPriceEntered(getPriceActual().multiply(getQtyOrdered()
				.divide(getQtyEntered(), 12, BigDecimal.ROUND_HALF_UP)));	//	recision
		
		//	Calculate Discount
		setDiscount(m_productPrice.getDiscount());
		//	Set UOM
		
		if( getC_UOM_ID() == 0 ){                           //Adempiere-122 changes
		     setC_UOM_ID(m_productPrice.getC_UOM_ID());
		}
	}	//	setPrice

	/**
	 * 	Get and calculate Product Pricing
	 *	@param M_PriceList_ID id
	 *	@return product pricing
	 */
	private MProductPricing getProductPricing (int M_PriceList_ID)
	{
		m_productPrice = new MProductPricing (getM_Product_ID(), 
			getC_BPartner_ID(), getQtyOrdered(), m_IsSOTrx, get_TrxName());
		m_productPrice.setM_PriceList_ID(M_PriceList_ID);
		m_productPrice.setPriceDate(getDateOrdered());
		//
		m_productPrice.calculatePrice();
		return m_productPrice;
	}	//	getProductPrice
	
	/**
	 *	Set Tax
	 *	@return true if tax is set
	 */
	public boolean setTax()
	{
		int ii = Tax.get(getCtx(), getM_Product_ID(), getC_Charge_ID(), getDateOrdered(), getDateOrdered(),
			getAD_Org_ID(), getM_Warehouse_ID(),
			getC_BPartner_Location_ID(),		//	should be bill to
			getC_BPartner_Location_ID(), m_IsSOTrx, get_TrxName());
		if (ii == 0)
		{
			log.log(Level.SEVERE, "No Tax found");
			return false;
		}
		setC_Tax_ID (ii);
		return true;
	}	//	setTax
	
	/**
	 * 	Calculate Extended Amt.
	 * 	May or may not include tax
	 */
	public void setLineNetAmt () {
		//	Line Net Amt
		BigDecimal lineNetAmount = null;
		if(getM_Product_ID() != 0) {
			MProduct product = MProduct.get(getCtx(), getM_Product_ID(), get_TrxName());
			if(product.getC_UOM_ID() != getC_UOM_ID()
					&& getPriceEntered() != null && !getPriceEntered().equals(Env.ZERO)
					&& getQtyEntered() != null && !getQtyEntered().equals(Env.ZERO)) {
				lineNetAmount = getQtyEntered().multiply(getPriceEntered());
			}
		}
		//	Set default
		if(lineNetAmount == null) {
			lineNetAmount = getPriceActual().multiply(getQtyOrdered());
		}
		boolean documentLevel = getTax().isDocumentLevel();
		
		//	juddm: Tax Exempt & Tax Included in Price List & not Document Level - Adjust Line Amount
		//  http://sourceforge.net/tracker/index.php?func=detail&aid=1733602&group_id=176962&atid=879332
		if (isTaxIncluded() && !documentLevel)	{
			BigDecimal taxStdAmt = Env.ZERO, taxThisAmt = Env.ZERO;
			
			MTax orderTax = getTax();
			MTax stdTax = null;
			
			//	get the standard tax
			if (getProduct() == null)
			{
				if (getCharge() != null)	// Charge 
				{
					stdTax = new MTax (getCtx(), 
							((MTaxCategory) getCharge().getC_TaxCategory()).getDefaultTax().getC_Tax_ID(),
							get_TrxName());
				}
					
			}
			else	// Product
				stdTax = new MTax (getCtx(), 
							((MTaxCategory) getProduct().getC_TaxCategory()).getDefaultTax().getC_Tax_ID(), 
							get_TrxName());

			if (stdTax != null)
			{
				log.fine("stdTax rate is " + stdTax.getRate());
				log.fine("orderTax rate is " + orderTax.getRate());
				
				taxThisAmt = taxThisAmt.add(orderTax.calculateTax(lineNetAmount, isTaxIncluded(), getPrecision()));
				taxStdAmt = taxStdAmt.add(stdTax.calculateTax(lineNetAmount, isTaxIncluded(), getPrecision()));
				
				lineNetAmount = lineNetAmount.subtract(taxStdAmt).add(taxThisAmt);
				
				log.fine("Price List includes Tax and Tax Changed on Order Line: New Tax Amt: " 
						+ taxThisAmt + " Standard Tax Amt: " + taxStdAmt + " Line Net Amt: " + lineNetAmount);	
			}
			
		}
		
		if (lineNetAmount.scale() > getPrecision())
			lineNetAmount = lineNetAmount.setScale(getPrecision(), BigDecimal.ROUND_HALF_UP);
		super.setLineNetAmt (lineNetAmount);
	}	//	setLineNetAmt
	
	/**
	 * 	Get Charge
	 *	@return product or null
	 */
	public MCharge getCharge()
	{
		if (m_charge == null && getC_Charge_ID() != 0)
			m_charge =  MCharge.get (getCtx(), getC_Charge_ID());
		return m_charge;
	}
	/**
	 * 	Get Tax
	 *	@return tax
	 */
	protected MTax getTax()
	{
		if (m_tax == null)
			m_tax = MTax.get(getCtx(), getC_Tax_ID());
		return m_tax;
	}	//	getTax

	/**
	 * 	Get Currency Precision from Currency
	 *	@return precision
	 */
	public int getPrecision()
	{
		if (m_precision != null)
			return m_precision.intValue();
		//
		if (getC_Currency_ID() == 0)
		{
			setOrder (getParent());
			if (m_precision != null)
				return m_precision.intValue();
		}
		if (getC_Currency_ID() != 0)
		{
			MCurrency cur = MCurrency.get(getCtx(), getC_Currency_ID());
			if (cur.get_ID() != 0)
			{
				m_precision = new Integer (cur.getStdPrecision());
				return m_precision.intValue();
			}
		}
		//	Fallback
		String sql = "SELECT c.StdPrecision "
			+ "FROM C_Currency c INNER JOIN C_Order x ON (x.C_Currency_ID=c.C_Currency_ID) "
			+ "WHERE x.C_Order_ID=?";
		int i = DB.getSQLValue(get_TrxName(), sql, getC_Order_ID());
		m_precision = new Integer(i);
		return m_precision.intValue();
	}	//	getPrecision
	
	/**
	 * 	Set Product
	 *	@param product product
	 */
	public void setProduct (MProduct product)
	{
		m_product = product;
		if (m_product != null)
		{
			setM_Product_ID(m_product.getM_Product_ID());
			setC_UOM_ID (m_product.getC_UOM_ID());
		}
		else
		{
			setM_Product_ID(0);
			set_ValueNoCheck ("C_UOM_ID", null);
		}
		setM_AttributeSetInstance_ID(0);
	}	//	setProduct

	
	/**
	 * 	Set M_Product_ID
	 *	@param M_Product_ID product
	 *	@param setUOM set also UOM
	 */
	public void setM_Product_ID (int M_Product_ID, boolean setUOM)
	{
		if (setUOM)
			setProduct(MProduct.get(getCtx(), M_Product_ID));
		else
			super.setM_Product_ID (M_Product_ID);
		setM_AttributeSetInstance_ID(0);
	}	//	setM_Product_ID
	
	/**
	 * 	Set Product and UOM
	 *	@param M_Product_ID product
	 *	@param C_UOM_ID uom
	 */
	public void setM_Product_ID (int M_Product_ID, int C_UOM_ID)
	{
		super.setM_Product_ID (M_Product_ID);
		if (C_UOM_ID != 0)
			super.setC_UOM_ID(C_UOM_ID);
		setM_AttributeSetInstance_ID(0);
	}	//	setM_Product_ID
	
	
	/**
	 * 	Get Product
	 *	@return product or null
	 */
	public MProduct getProduct()
	{
		if (m_product == null && getM_Product_ID() != 0)
			m_product =  MProduct.get (getCtx(), getM_Product_ID(), get_TrxName());
		return m_product;
	}	//	getProduct
	
	/**
	 * 	Set M_AttributeSetInstance_ID
	 *	@param M_AttributeSetInstance_ID id
	 */
	public void setM_AttributeSetInstance_ID (int M_AttributeSetInstance_ID)
	{
		if (M_AttributeSetInstance_ID == 0)		//	 0 is valid ID
			set_Value("M_AttributeSetInstance_ID", new Integer(0));
		else
			super.setM_AttributeSetInstance_ID (M_AttributeSetInstance_ID);
	}	//	setM_AttributeSetInstance_ID
	
	/**
	 * 	Set Warehouse
	 *	@param M_Warehouse_ID warehouse
	 */
	public void setM_Warehouse_ID (int M_Warehouse_ID)
	{
		if (getM_Warehouse_ID() > 0
			&& getM_Warehouse_ID() != M_Warehouse_ID
			&& !canChangeWarehouse())
			log.severe("Ignored - Already Delivered/Invoiced/Reserved");
		else
			super.setM_Warehouse_ID (M_Warehouse_ID);
	}	//	setM_Warehouse_ID
	
	/**
	 * 	Can Change Warehouse
	 *	@return true if warehouse can be changed
	 */
	public boolean canChangeWarehouse()
	{
		if (getQtyDelivered().signum() != 0)
		{
			log.saveError("Error", Msg.translate(getCtx(), "QtyDelivered") + "=" + getQtyDelivered());
			return false;
		}
		if (getQtyReserved().signum() != 0)
		{
			log.saveError("Error", Msg.translate(getCtx(), "QtyReserved") + "=" + getQtyReserved());
			return false;
		}
		//	We can change
		return true;
	}	//	canChangeWarehouse

	/**
	 * 	Get AD_OrgTrx_ID
	 *	@return trx org
	 */
	public int getAD_OrgTrx_ID()
	{
		int ii = super.getAD_OrgTrx_ID();
		if (ii == 0)
			ii = getParent().getAD_OrgTrx_ID();
		return ii;
	}	//	getAD_OrgTrx_ID

	/**************************************************************************
	 * 	String Representation
	 * 	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("MOrderLine[")
			.append(get_ID())
			.append(", Line=").append(getLine())
			.append(", Ordered=").append(getQtyOrdered())
			.append(", Delivered=").append(getQtyDelivered())
			.append(", Invoiced=").append(getQtyInvoiced())
			.append(", Reserved=").append(getQtyReserved())
			.append(", LineNet=").append(getLineNetAmt())
			.append ("]");
		return sb.toString ();
	}	//	toString

	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription
	
	/**
	 * 	Get Description Text.
	 * 	For jsp access (vs. isDescription)
	 *	@return description
	 */
	public String getDescriptionText()
	{
		return super.getDescription();
	}	//	getDescriptionText
	
	/**
	 * 	Get Name
	 *	@return get the name of the line (from Product)
	 */
	public String getName()
	{
		getProduct();
		if (m_product != null)
			return m_product.getName();
		if (getC_Charge_ID() != 0)
		{
			MCharge charge = MCharge.get(getCtx(), getC_Charge_ID());
			return charge.getName();
		}
		return "";
	}	//	getName

	/**
	 * 	Set C_Charge_ID
	 *	@param C_Charge_ID charge
	 */
	public void setC_Charge_ID (int C_Charge_ID)
	{
		super.setC_Charge_ID (C_Charge_ID);
		if (C_Charge_ID > 0)
			set_ValueNoCheck ("C_UOM_ID", null);
	}	//	setC_Charge_ID
	/**
	 *	Set Discount
	 */
	public void setDiscount()
	{
		BigDecimal list = getPriceList();
		//	No List Price
		if (Env.ZERO.compareTo(list) == 0)
			return;
		BigDecimal discount = list.subtract(getPriceActual())
			.multiply(new BigDecimal(100))
			.divide(list, getPrecision(), BigDecimal.ROUND_HALF_UP);
		setDiscount(discount);
	}	//	setDiscount

	/**
	 *	Is Tax Included in Amount
	 *	@return true if tax calculated
	 */
	public boolean isTaxIncluded()
	{
		if (m_M_PriceList_ID == 0)
		{
			m_M_PriceList_ID = DB.getSQLValue(get_TrxName(),
				"SELECT M_PriceList_ID FROM C_Order WHERE C_Order_ID=?",
				getC_Order_ID());
		}
		MPriceList pl = MPriceList.get(getCtx(), m_M_PriceList_ID, get_TrxName());
		return pl.isTaxIncluded();
	}	//	isTaxIncluded

	
	/**
	 * 	Set Qty Entered/Ordered.
	 * 	Use this Method if the Line UOM is the Product UOM 
	 *	@param Qty QtyOrdered/Entered
	 */
	public void setQty (BigDecimal Qty)
	{
		super.setQtyEntered (Qty);
		super.setQtyOrdered (getQtyEntered());
	}	//	setQty

	/**
	 * 	Set Qty Entered - enforce entered UOM 
	 *	@param QtyEntered
	 */
	public void setQtyEntered (BigDecimal QtyEntered)
	{
		if (QtyEntered != null && getC_UOM_ID() != 0)
		{
			int precision = MUOM.getPrecision(getCtx(), getC_UOM_ID());
			QtyEntered = QtyEntered.setScale(precision, BigDecimal.ROUND_HALF_UP);
		}
		super.setQtyEntered (QtyEntered);
	}	//	setQtyEntered

	/**
	 * 	Set Qty Ordered - enforce Product UOM 
	 *	@param QtyOrdered
	 */
	public void setQtyOrdered (BigDecimal QtyOrdered)
	{
		MProduct product = getProduct();
		if (QtyOrdered != null && product != null)
		{
			int precision = product.getUOMPrecision();
			QtyOrdered = QtyOrdered.setScale(precision, BigDecimal.ROUND_HALF_UP);
		}
		super.setQtyOrdered(QtyOrdered);
	}	//	setQtyOrdered

	/**
	 * Set reference for RMA
	 * @param inOutLineReference
	 */
	public void setRef_InOutLine(MInOutLine inOutLineReference) {
		setRef_InOutLine_ID(inOutLineReference.getM_InOutLine_ID());
		//	Charge
		if(inOutLineReference.getC_Charge_ID() != 0) {
			setC_Charge_ID(inOutLineReference.getC_Charge_ID());
		}
		//	Product
		if(inOutLineReference.getM_Product_ID() != 0) {
			setM_Product_ID(inOutLineReference.getM_Product_ID());
			if(inOutLineReference.getM_AttributeSetInstance_ID() > 0) {
				setM_AttributeSetInstance_ID(inOutLineReference.getM_AttributeSetInstance_ID());
			}
		}
		if(inOutLineReference.getC_UOM_ID() != 0) {
			setC_UOM_ID(inOutLineReference.getC_UOM_ID());
		}
		if(inOutLineReference.getAD_OrgTrx_ID() != 0) {
			setAD_OrgTrx_ID(inOutLineReference.getAD_OrgTrx_ID());
		}
		if(inOutLineReference.getC_Project_ID() != 0) {
			setC_Project_ID(inOutLineReference.getC_Project_ID());
		}
		if(inOutLineReference.getC_Campaign_ID() != 0) {
			setC_Campaign_ID(inOutLineReference.getC_Campaign_ID());
		}
		if(inOutLineReference.getC_Activity_ID() != 0) {
			setC_Activity_ID(inOutLineReference.getC_Activity_ID());
		}
		if(inOutLineReference.getUser1_ID() != 0) {
			setUser1_ID(inOutLineReference.getUser1_ID());
		}
		if(inOutLineReference.getUser2_ID() != 0) {
			setUser2_ID(inOutLineReference.getUser2_ID());
		}
		if(inOutLineReference.getUser3_ID() != 0) {
			setUser3_ID(inOutLineReference.getUser3_ID());
		}
		if(inOutLineReference.getUser4_ID() != 0) {
			setUser4_ID(inOutLineReference.getUser4_ID());
		}
		int invoiceLineReferenceId = inOutLineReference.getInvoiceLineId();
		//	Set Price from Invoice / Order
		if (invoiceLineReferenceId != 0) {
            MInvoiceLine invoiceLine = new MInvoiceLine(getCtx(), invoiceLineReferenceId, get_TrxName());
            setPriceList(invoiceLine.getPriceList());
            setPriceEntered(invoiceLine.getPriceEntered());
            setPriceActual(invoiceLine.getPriceActual());
            setC_Tax_ID(invoiceLine.getC_Tax_ID());
        } else if (inOutLineReference.getC_OrderLine_ID() != 0) {
            MOrderLine orderLine = new MOrderLine (getCtx(), inOutLineReference.getC_OrderLine_ID(), get_TrxName());
            setPriceList(orderLine.getPriceList());
            setPriceEntered(orderLine.getPriceEntered());
            setPriceActual(orderLine.getPriceActual());
            setC_Tax_ID(orderLine.getC_Tax_ID());
        }
	}
	
	/**
	 * Set reference for RMA
	 * @param invoiceLineReference
	 */
	public void setRef_InvoiceLine(MInvoiceLine invoiceLineReference) {
		//	Charge
		if(invoiceLineReference.getC_Charge_ID() != 0) {
			setC_Charge_ID(invoiceLineReference.getC_Charge_ID());
		}
		//	Product
		if(invoiceLineReference.getM_Product_ID() != 0) {
			setM_Product_ID(invoiceLineReference.getM_Product_ID());
			if(invoiceLineReference.getM_AttributeSetInstance_ID() > 0) {
				setM_AttributeSetInstance_ID(invoiceLineReference.getM_AttributeSetInstance_ID());
			}
		}
		if(invoiceLineReference.getC_UOM_ID() != 0) {
			setC_UOM_ID(invoiceLineReference.getC_UOM_ID());
		}
		if(invoiceLineReference.getAD_OrgTrx_ID() != 0) {
			setAD_OrgTrx_ID(invoiceLineReference.getAD_OrgTrx_ID());
		}
		if(invoiceLineReference.getC_Project_ID() != 0) {
			setC_Project_ID(invoiceLineReference.getC_Project_ID());
		}
		if(invoiceLineReference.getC_Campaign_ID() != 0) {
			setC_Campaign_ID(invoiceLineReference.getC_Campaign_ID());
		}
		if(invoiceLineReference.getC_Activity_ID() != 0) {
			setC_Activity_ID(invoiceLineReference.getC_Activity_ID());
		}
		if(invoiceLineReference.getUser1_ID() != 0) {
			setUser1_ID(invoiceLineReference.getUser1_ID());
		}
		if(invoiceLineReference.getUser2_ID() != 0) {
			setUser2_ID(invoiceLineReference.getUser2_ID());
		}
		if(invoiceLineReference.getUser3_ID() != 0) {
			setUser3_ID(invoiceLineReference.getUser3_ID());
		}
		if(invoiceLineReference.getUser4_ID() != 0) {
			setUser4_ID(invoiceLineReference.getUser4_ID());
		}
		setRef_InvoiceLine_ID(invoiceLineReference.getC_InvoiceLine_ID());
		//	Set Price from Invoice / Order
		setPriceList(invoiceLineReference.getPriceList());
		setPriceEntered(invoiceLineReference.getPriceEntered());
        setPriceActual(invoiceLineReference.getPriceActual());
        setC_Tax_ID(invoiceLineReference.getC_Tax_ID());
        int inOutLineReferenceId = invoiceLineReference.getM_InOutLine_ID();
        if(inOutLineReferenceId == 0) {
        	inOutLineReferenceId = invoiceLineReference.getInOutLineId();
        }
		//	Set Price from Invoice / Order
		if (inOutLineReferenceId != 0) {
            setRef_InOutLine_ID(inOutLineReferenceId);
        }
	}
	
	/**************************************************************************
	 * 	Before Save
	 *	@param newRecord
	 *	@return true if it can be saved
	 */
	protected boolean beforeSave (boolean newRecord)
	{
		if (newRecord && getParent().isComplete()) {
			log.saveError("ParentComplete", Msg.translate(getCtx(), "C_OrderLine"));
			return false;
		}
		//	Get Defaults from Parent
		if (getC_BPartner_ID() == 0 || getC_BPartner_Location_ID() == 0
			|| getM_Warehouse_ID() == 0 
			|| getC_Currency_ID() == 0)
			setOrder (getParent());
		if (m_M_PriceList_ID == 0)
			setHeaderInfo(getParent());

		
		//	R/O Check - Product/Warehouse Change
		if (!newRecord 
			&& (is_ValueChanged("M_Product_ID") || is_ValueChanged("M_Warehouse_ID"))) 
		{
			if (!canChangeWarehouse())
				return false;
		}	//	Product Changed
		
		//	Charge
		if (getC_Charge_ID() != 0 && getM_Product_ID() != 0)
				setM_Product_ID(0);
		//	No Product
		if (getM_Product_ID() == 0)
			setM_AttributeSetInstance_ID(0);
		//	Product
		else	//	Set/check Product Price
		{
			//	Set Price if Actual = 0
			if (m_productPrice == null 
				&&  Env.ZERO.compareTo(getPriceActual()) == 0
				&&  Env.ZERO.compareTo(getPriceList()) == 0)
				setPrice();
			//	Check if on Price list
			if (m_productPrice == null)
				getProductPricing(m_M_PriceList_ID);
			if (!m_productPrice.isCalculated()
					&& !isProcessed()
					&& (newRecord
							|| is_ValueChanged(COLUMNNAME_M_Product_ID)
							|| is_ValueChanged(COLUMNNAME_C_UOM_ID)
							|| is_ValueChanged(COLUMNNAME_QtyEntered)
							|| is_ValueChanged(COLUMNNAME_PriceEntered)
							|| is_ValueChanged(COLUMNNAME_Discount)
							|| is_ValueChanged(COLUMNNAME_PriceEntered))) {
				MDocType documentType = MDocType.get(getCtx(), getParent().getC_DocTypeTarget_ID());
				if(Util.isEmpty(documentType.getDocSubTypeSO())
						|| !documentType.getDocSubTypeSO().equals(MDocType.DOCSUBTYPESO_ReturnMaterial)) {
					throw new ProductNotOnPriceListException(m_productPrice, getLine());
				}
			}
		}

		//	UOM
		if (getC_UOM_ID() == 0 
			&& (getM_Product_ID() != 0 
				|| getPriceEntered().compareTo(Env.ZERO) != 0
				|| getC_Charge_ID() != 0))
		{
			int C_UOM_ID = MUOM.getDefault_UOM_ID(getCtx());
			if (C_UOM_ID > 0)
				setC_UOM_ID (C_UOM_ID);
		}
		//	Qty Precision
		if (newRecord || is_ValueChanged("QtyEntered"))
			setQtyEntered(getQtyEntered());
		if (newRecord || is_ValueChanged("QtyOrdered"))
			setQtyOrdered(getQtyOrdered());
		
		//	Qty on instance ASI for SO
		if (getParent().isSOTrx()
			&& getM_AttributeSetInstance_ID() != 0
			&& (newRecord || is_ValueChanged("M_Product_ID")
				|| is_ValueChanged("M_AttributeSetInstance_ID")
				|| is_ValueChanged("M_Warehouse_ID")))
		{
			MProduct product = getProduct();
			if (product.isStocked())
			{
				int M_AttributeSet_ID = product.getM_AttributeSet_ID();
				boolean isInstance = M_AttributeSet_ID != 0;
				if (isInstance)
				{
					MAttributeSet mas = MAttributeSet.get(getCtx(), M_AttributeSet_ID);
					isInstance = mas.isInstanceAttribute();
				}
				//	Max
				if (isInstance)
				{
					MStorage[] storages = MStorage.getWarehouse(getCtx(), 
						getM_Warehouse_ID(), getM_Product_ID(), getM_AttributeSetInstance_ID(), 
						M_AttributeSet_ID, false, null, true, get_TrxName());
					BigDecimal qty = Env.ZERO;
					for (int i = 0; i < storages.length; i++)
					{
						if (storages[i].getM_AttributeSetInstance_ID() == getM_AttributeSetInstance_ID())
							qty = qty.add(storages[i].getQtyOnHand());
					}
					
					if (getQtyOrdered().compareTo(qty) > 0)
					{
						log.warning("Qty - Stock=" + qty + ", Ordered=" + getQtyOrdered());
						log.saveError("QtyInsufficient", "=" + qty); 
						return false;
					}
				}
			}	//	stocked
		}	//	SO instance
		
		//	FreightAmt Not used
		if (Env.ZERO.compareTo(getFreightAmt()) != 0)
			setFreightAmt(Env.ZERO);

		//	Set Tax
		if (getC_Tax_ID() == 0)
			setTax();

		//	Get Line No
		if (getLine() == 0)
		{
			String sql = "SELECT COALESCE(MAX(Line),0)+10 FROM C_OrderLine WHERE C_Order_ID=?";
			int ii = DB.getSQLValue (get_TrxName(), sql, getC_Order_ID());
			setLine (ii);
		}
		
		//	Calculations & Rounding
		setLineNetAmt();	//	extended Amount with or without tax
		setDiscount();

		return true;
	}	//	beforeSave

	
	/**
	 * 	Before Delete
	 *	@return true if it can be deleted
	 */
	protected boolean beforeDelete ()
	{
		//	R/O Check - Something delivered. etc.
		if (Env.ZERO.compareTo(getQtyDelivered()) != 0)
		{
			log.saveError("DeleteError", Msg.translate(getCtx(), "QtyDelivered") + "=" + getQtyDelivered());
			return false;
		}
		if (Env.ZERO.compareTo(getQtyInvoiced()) != 0)
		{
			log.saveError("DeleteError", Msg.translate(getCtx(), "QtyInvoiced") + "=" + getQtyInvoiced());
			return false;
		}
		if (Env.ZERO.compareTo(getQtyReserved()) != 0)
		{
			//	For PO should be On Order
			log.saveError("DeleteError", Msg.translate(getCtx(), "QtyReserved") + "=" + getQtyReserved());
			return false;
		}
		
		// UnLink All Requisitions
		MRequisitionLine.unlinkC_OrderLine_ID(getCtx(), get_ID(), get_TrxName());
		
		return true;
	}	//	beforeDelete
	
	/**
	 * 	After Save
	 *	@param newRecord new
	 *	@param success success
	 *	@return saved
	 */
	protected boolean afterSave (boolean newRecord, boolean success)
	{
		if (!success)
			return success;
		if (newRecord
				|| (!newRecord && is_ValueChanged(MOrderLine.COLUMNNAME_C_Tax_ID) && !getParent().isProcessed())
				|| (!newRecord && is_ValueChanged(MOrderLine.COLUMNNAME_QtyEntered) && !getParent().isProcessed())
				|| (!newRecord && is_ValueChanged(MOrderLine.COLUMNNAME_PriceActual) && !getParent().isProcessed())
		)
			return updateHeaderTax();

		return true;
	}	//	afterSave

	/**
	 * 	After Delete
	 *	@param success success
	 *	@return deleted
	 */
	protected boolean afterDelete (boolean success)
	{
		if (!success)
			return success;
		if (getS_ResourceAssignment_ID() != 0)
		{
			MResourceAssignment ra = new MResourceAssignment(getCtx(), getS_ResourceAssignment_ID(), get_TrxName());
			ra.delete(true);
		}
		
		return updateHeaderTax();
	}	//	afterDelete
	
	/**
	 *	Update Tax & Header
	 *	@return true if header updated
	 */
	private boolean updateHeaderTax()
	{
		//	Recalculate Tax for this Tax
		if (!getParent().isProcessed())
				getParent().calculateTaxTotal();
		
		//	Update Order Header
		String sql = "UPDATE C_Order i"
			+ " SET TotalLines="
				+ "(SELECT COALESCE(SUM(LineNetAmt),0) FROM C_OrderLine il WHERE i.C_Order_ID=il.C_Order_ID) "
			+ "WHERE C_Order_ID=" + getC_Order_ID();
		int no = DB.executeUpdate(sql, get_TrxName());
		if (no != 1)
			log.warning("(1) #" + no);

		if (isTaxIncluded())
			sql = "UPDATE C_Order i "
				+ " SET GrandTotal=TotalLines "
				+ "WHERE C_Order_ID=" + getC_Order_ID();
		else
			sql = "UPDATE C_Order i "
				+ " SET GrandTotal=TotalLines+"
					+ "(SELECT COALESCE(SUM(TaxAmt),0) FROM C_OrderTax it WHERE i.C_Order_ID=it.C_Order_ID) "
					+ "WHERE C_Order_ID=" + getC_Order_ID();
		no = DB.executeUpdate(sql, get_TrxName());
		if (no != 1)
			log.warning("(2) #" + no);
		m_parent = null;
		return no == 1;
	}	//	updateHeaderTax

	public void reserveStock() {
		//	Binding
		BigDecimal target = getParent().isBinding() ? getQtyOrdered() : Env.ZERO;
		BigDecimal difference = target.subtract(getQtyReserved()).subtract(getQtyDelivered());
		if (difference.signum() == 0)
			return;

		log.fine("Line=" + getLine()
				+ " - Target=" + target + ",Difference=" + difference
				+ " - Ordered=" + getQtyOrdered()
				+ ",Reserved=" + getQtyReserved() + ",Delivered=" + getQtyDelivered());

		//	Check Product - Stocked and Item
		MProduct product = getProduct();
		if (product != null) {
			if (product.isStocked()) {
				//	Mandatory Product Attribute Set Instance
				MAttributeSet.validateAttributeSetInstanceMandatory(product, Table_ID, isSOTrx(), getM_AttributeSetInstance_ID());
				BigDecimal ordered = isSOTrx() ? Env.ZERO : difference;
				BigDecimal reserved = isSOTrx() ? difference : Env.ZERO;
				//	update line
				setQtyReserved(getQtyReserved().add(difference));
				int locatorId = 0;
				//	Get Locator to reserve
				if (getM_AttributeSetInstance_ID() != 0)    //	Get existing Location
					locatorId = MStorage.getM_Locator_ID(getM_Warehouse_ID(),
							getM_Product_ID(), getM_AttributeSetInstance_ID(),
							ordered, get_TrxName());
				//	Get default Location
				if (locatorId == 0) {
					// try to take default locator for product first
					// if it is from the selected warehouse
					MWarehouse warehouse = MWarehouse.get(getCtx(), getM_Warehouse_ID());
					locatorId = product.getM_Locator_ID();
					if (locatorId != 0) {
						MLocator locator = new MLocator(getCtx(), product.getM_Locator_ID(), get_TrxName());
						//product has default locator defined but is not from the order warehouse
						if (locator.getM_Warehouse_ID() != warehouse.get_ID()) {
							locatorId = warehouse.getDefaultLocator().getM_Locator_ID();
						}
					} else {
						locatorId = warehouse.getDefaultLocator().getM_Locator_ID();
					}
				}
				//	Update Storage
				MStorage.add(getCtx(), getM_Warehouse_ID(), locatorId,
						getM_Product_ID(),
						getM_AttributeSetInstance_ID(), getM_AttributeSetInstance_ID(),
						Env.ZERO, reserved, ordered, get_TrxName());
			}    //	stocked
		}
	}

	@Override
	public int getM_Locator_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BigDecimal getMovementQty()
	{
		return this.getQtyEntered();
	}

	public BigDecimal getQtyToDelivery() {
		return getQtyOrdered().subtract(getQtyDelivered());
	}

	@Override
	public int getReversalLine_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isSOTrx() {
		return getParent().isSOTrx();
	}

	@Override
	public void setM_Locator_ID(int M_Locator_ID) {
		// TODO Auto-generated method stub

	}

	@Override
	public Timestamp getDateAcct() {
		return getParent().getDateAcct();
	}


	public IDocumentLine getReversalDocumentLine() {
		return null;
	}

	@Override
	public int getM_AttributeSetInstanceTo_ID() {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int getM_LocatorTo_ID() {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int getC_DocType_ID() {
		return getParent().getC_DocType_ID();
	}

	@Override
	public BigDecimal getPriceActualCurrency() {
		return getPriceActual();
	}

	@Override
	public int getC_Currency_ID ()
	{
		return getParent().getC_Currency_ID();
	}

	@Override
	public int getC_ConversionType_ID()
	{
		return getParent().getC_ConversionType_ID();
	}

	@Override
	public boolean isReversalParent() {
		// TODO Auto-generated method stub
		return getC_OrderLine_ID() < getReversalLine_ID();
	}
}	//	MOrderLine
