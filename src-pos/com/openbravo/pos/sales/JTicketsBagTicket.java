//    Openbravo POS is a point of sales application designed for touch screens.
//    Copyright (C) 2007-2009 Openbravo, S.L.
//    http://www.openbravo.com/product/pos
//
//    This file is part of Openbravo POS.
//
//    Openbravo POS is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    Openbravo POS is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with Openbravo POS.  If not, see <http://www.gnu.org/licenses/>.
package com.openbravo.pos.sales;

import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import com.openbravo.data.gui.MessageInf;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.printer.*;
import com.openbravo.basic.BasicException;
import com.openbravo.data.gui.JMessageDialog;
import com.openbravo.data.loader.SentenceList;
import com.openbravo.pos.customers.DataLogicCustomers;
import com.openbravo.pos.forms.DataLogicSales;
import com.openbravo.pos.scripting.ScriptEngine;
import com.openbravo.pos.scripting.ScriptException;
import com.openbravo.pos.scripting.ScriptFactory;
import com.openbravo.pos.forms.DataLogicSystem;
import com.openbravo.pos.panels.JTicketsFinder;
import com.openbravo.pos.payment.PaymentInfo;
import com.openbravo.pos.ticket.FindTicketsInfo;
import com.openbravo.pos.ticket.TaxInfo;
import com.openbravo.pos.ticket.TicketTaxInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JTicketsBagTicket extends JTicketsBag {

    private DataLogicSystem m_dlSystem = null;
    protected DataLogicCustomers dlCustomers = null;

    private DeviceTicket m_TP;
    private TicketParser m_TTP;
    private TicketParser m_TTP2;

    private TicketInfo m_ticket;
    private TicketInfo m_ticketCopy;

    private JTicketsBagTicketBag m_TicketsBagTicketBag;

    private JPanelTicketEdits m_panelticketedit;

    /**
     * Creates new form JTicketsBagTicket
     */
    public JTicketsBagTicket(AppView app, JPanelTicketEdits panelticket) {

        super(app, panelticket);
        m_panelticketedit = panelticket;
        m_dlSystem = (DataLogicSystem) m_App.getBean("com.openbravo.pos.forms.DataLogicSystem");
        dlCustomers = (DataLogicCustomers) m_App.getBean("com.openbravo.pos.customers.DataLogicCustomers");

        // Inicializo la impresora...
        m_TP = new DeviceTicket();

        // Inicializo el parser de documentos de ticket
        m_TTP = new TicketParser(m_TP, m_dlSystem); // para visualizar el ticket
        m_TTP2 = new TicketParser(m_App.getDeviceTicket(), m_dlSystem); // para imprimir el ticket

        initComponents();

        m_TicketsBagTicketBag = new JTicketsBagTicketBag(this);

        m_jTicketEditor.addEditorKeys(m_jKeys);

        // Este deviceticket solo tiene una impresora, la de pantalla
        m_jPanelTicket.add(m_TP.getDevicePrinter("1").getPrinterComponent(), BorderLayout.CENTER);
    }

    public void activate() {

        // precondicion es que no tenemos ticket activado ni ticket en el panel
        m_ticket = null;
        m_ticketCopy = null;

        printTicket();

        m_jTicketEditor.reset();
        m_jTicketEditor.activate();

        m_panelticketedit.setActiveTicket(null, null);

        jrbSales.setSelected(true);

        m_jEdit.setVisible(m_App.getAppUserView().getUser().hasPermission("sales.EditTicket"));
        m_jRefund.setVisible(m_App.getAppUserView().getUser().hasPermission("sales.RefundTicket"));
        m_jPrint.setVisible(m_App.getAppUserView().getUser().hasPermission("sales.PrintTicket"));

        // postcondicion es que tenemos ticket activado aqui y ticket en el panel
    }

    public boolean deactivate() {

        // precondicion es que tenemos ticket activado aqui y ticket en el panel        
        m_ticket = null;
        m_ticketCopy = null;
        return true;
        // postcondicion es que no tenemos ticket activado ni ticket en el panel
    }

    public void deleteTicket() {

        if (m_ticketCopy != null) {
            // Para editar borramos el ticket anterior
            try {
                m_dlSales.deleteTicket(m_ticketCopy, m_App.getInventoryLocation());
            } catch (BasicException eData) {
                MessageInf msg = new MessageInf(MessageInf.SGN_NOTICE, AppLocal.getIntString("message.nosaveticket"), eData);
                msg.show(this);
            }
        }

        m_ticket = null;
        m_ticketCopy = null;
        resetToTicket();
    }

    public void canceleditionTicket() {

        m_ticketCopy = null;
        resetToTicket();
    }

    private void resetToTicket() {
        printTicket();
        m_jTicketEditor.reset();
        m_jTicketEditor.activate();
        m_panelticketedit.setActiveTicket(null, null);
    }

    protected JComponent getBagComponent() {
        return m_TicketsBagTicketBag;
    }

    protected JComponent getNullComponent() {
        return this;
    }

    private void readTicket(int iTicketid, int iTickettype) {

        try {
            TicketInfo ticket = (iTicketid == -1)
                    ? m_dlSales.loadTicket(iTickettype, m_jTicketEditor.getValueInteger())
                    : m_dlSales.loadTicket(iTickettype, iTicketid);

            if (ticket == null) {
                MessageInf msg = new MessageInf(MessageInf.SGN_WARNING, AppLocal.getIntString("message.notexiststicket"));
                msg.show(this);
            } else {
                m_ticket = ticket;
                m_ticketCopy = null; // se asigna al pulsar el boton de editar o devolver
                printTicket();
            }

        } catch (BasicException e) {
            MessageInf msg = new MessageInf(MessageInf.SGN_WARNING, AppLocal.getIntString("message.cannotloadticket"), e);
            msg.show(this);
        }

        m_jTicketEditor.reset();
        m_jTicketEditor.activate();
    }

    private void printTicket() {

        // imprimo m_ticket
        try {
            m_jEdit.setEnabled(
                    m_ticket != null
                    && (m_ticket.getTicketType() == TicketInfo.RECEIPT_NORMAL || m_ticket.getTicketType() == TicketInfo.RECEIPT_REFUND)
                    && m_dlSales.isCashActive(m_ticket.getActiveCash()));
//          Añadido para no editar
//            m_jEdit.setEnabled(false);
        } catch (BasicException e) {
            m_jEdit.setEnabled(false);
        }
        m_jRefund.setEnabled(m_ticket != null && m_ticket.getTicketType() == TicketInfo.RECEIPT_NORMAL);
        m_jPrint.setEnabled(m_ticket != null);

        // Este deviceticket solo tiene una impresora, la de pantalla
        m_TP.getDevicePrinter("1").reset();

        if (m_ticket == null) {
            m_jTicketId.setText(null);
        } else {
            m_jTicketId.setText(m_ticket.getName());

            try {
                ScriptEngine script = ScriptFactory.getScriptEngine(ScriptFactory.VELOCITY);
                script.put("ticket", m_ticket);
                m_TTP.printTicket(script.eval(m_dlSystem.getResourceAsXML("Printer.TicketPreview")).toString());
            } catch (ScriptException e) {
                MessageInf msg = new MessageInf(MessageInf.SGN_WARNING, AppLocal.getIntString("message.cannotprintticket"), e);
                msg.show(this);
            } catch (TicketPrinterException eTP) {
                MessageInf msg = new MessageInf(MessageInf.SGN_WARNING, AppLocal.getIntString("message.cannotprintticket"), eTP);
                msg.show(this);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        m_jOptions = new javax.swing.JPanel();
        m_jButtons = new javax.swing.JPanel();
        m_jTicketId = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();
        m_jEdit = new javax.swing.JButton();
        m_jRefund = new javax.swing.JButton();
        m_jPrint = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        m_jPanelTicket = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        m_jKeys = new com.openbravo.editor.JEditorKeys();
        jPanel5 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        m_jTicketEditor = new com.openbravo.editor.JEditorIntegerPositive();
        jPanel1 = new javax.swing.JPanel();
        jrbSales = new javax.swing.JRadioButton();
        jrbRefunds = new javax.swing.JRadioButton();

        setLayout(new java.awt.BorderLayout());

        m_jOptions.setLayout(new java.awt.BorderLayout());

        m_jButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        m_jTicketId.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        m_jTicketId.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(javax.swing.UIManager.getDefaults().getColor("Button.darkShadow")), javax.swing.BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        m_jTicketId.setOpaque(true);
        m_jTicketId.setPreferredSize(new java.awt.Dimension(160, 25));
        m_jTicketId.setRequestFocusEnabled(false);
        m_jButtons.add(m_jTicketId);

        jButton2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/search.png"))); // NOI18N
        jButton2.setText(AppLocal.getIntString("label.search")); // NOI18N
        jButton2.setFocusPainted(false);
        jButton2.setFocusable(false);
        jButton2.setMargin(new java.awt.Insets(8, 14, 8, 14));
        jButton2.setRequestFocusEnabled(false);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        m_jButtons.add(jButton2);

        m_jEdit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/edit.png"))); // NOI18N
        m_jEdit.setText(AppLocal.getIntString("button.edit")); // NOI18N
        m_jEdit.setEnabled(false);
        m_jEdit.setFocusPainted(false);
        m_jEdit.setFocusable(false);
        m_jEdit.setMargin(new java.awt.Insets(8, 14, 8, 14));
        m_jEdit.setRequestFocusEnabled(false);
        m_jEdit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_jEditActionPerformed(evt);
            }
        });
        m_jButtons.add(m_jEdit);

        m_jRefund.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/inbox.png"))); // NOI18N
        m_jRefund.setText(AppLocal.getIntString("button.refund")); // NOI18N
        m_jRefund.setFocusPainted(false);
        m_jRefund.setFocusable(false);
        m_jRefund.setMargin(new java.awt.Insets(8, 14, 8, 14));
        m_jRefund.setRequestFocusEnabled(false);
        m_jRefund.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_jRefundActionPerformed(evt);
            }
        });
        m_jButtons.add(m_jRefund);

        m_jPrint.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/yast_printer.png"))); // NOI18N
        m_jPrint.setText(AppLocal.getIntString("button.print")); // NOI18N
        m_jPrint.setFocusPainted(false);
        m_jPrint.setFocusable(false);
        m_jPrint.setMargin(new java.awt.Insets(8, 14, 8, 14));
        m_jPrint.setRequestFocusEnabled(false);
        m_jPrint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_jPrintActionPerformed(evt);
            }
        });
        m_jButtons.add(m_jPrint);

        m_jOptions.add(m_jButtons, java.awt.BorderLayout.WEST);

        jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        m_jOptions.add(jPanel2, java.awt.BorderLayout.CENTER);

        add(m_jOptions, java.awt.BorderLayout.NORTH);

        m_jPanelTicket.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        m_jPanelTicket.setLayout(new java.awt.BorderLayout());
        add(m_jPanelTicket, java.awt.BorderLayout.CENTER);

        jPanel3.setLayout(new java.awt.BorderLayout());

        jPanel4.setLayout(new javax.swing.BoxLayout(jPanel4, javax.swing.BoxLayout.Y_AXIS));

        m_jKeys.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_jKeysActionPerformed(evt);
            }
        });
        jPanel4.add(m_jKeys);

        jPanel5.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        jPanel5.setLayout(new java.awt.GridBagLayout());

        jButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/button_ok.png"))); // NOI18N
        jButton1.setFocusPainted(false);
        jButton1.setFocusable(false);
        jButton1.setMargin(new java.awt.Insets(8, 14, 8, 14));
        jButton1.setRequestFocusEnabled(false);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanel5.add(jButton1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        jPanel5.add(m_jTicketEditor, gridBagConstraints);

        jPanel4.add(jPanel5);

        jPanel3.add(jPanel4, java.awt.BorderLayout.NORTH);

        buttonGroup1.add(jrbSales);
        jrbSales.setText(AppLocal.getIntString("label.sales")); // NOI18N
        jrbSales.setFocusPainted(false);
        jrbSales.setFocusable(false);
        jrbSales.setRequestFocusEnabled(false);
        jPanel1.add(jrbSales);

        buttonGroup1.add(jrbRefunds);
        jrbRefunds.setText(AppLocal.getIntString("label.refunds")); // NOI18N
        jrbRefunds.setFocusPainted(false);
        jrbRefunds.setFocusable(false);
        jrbRefunds.setRequestFocusEnabled(false);
        jPanel1.add(jrbRefunds);

        jPanel3.add(jPanel1, java.awt.BorderLayout.CENTER);

        add(jPanel3, java.awt.BorderLayout.EAST);
    }// </editor-fold>//GEN-END:initComponents

    private void m_jEditActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_jEditActionPerformed

        m_ticketCopy = m_ticket;
        m_TicketsBagTicketBag.showEdit();
        m_panelticketedit.showCatalog();
        m_panelticketedit.setActiveTicket(m_ticket.copyTicket(), null);

    }//GEN-LAST:event_m_jEditActionPerformed

    private void m_jPrintActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_jPrintActionPerformed

        if (m_ticket != null) {
            try {
                ScriptEngine script = ScriptFactory.getScriptEngine(ScriptFactory.VELOCITY);
                script.put("ticket", m_ticket);
                m_TTP2.printTicket(script.eval(m_dlSystem.getResourceAsXML("Printer.TicketPreview")).toString());
            } catch (ScriptException e) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_NOTICE, AppLocal.getIntString("message.cannotprint"), e));
            } catch (TicketPrinterException e) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_NOTICE, AppLocal.getIntString("message.cannotprint"), e));
            }
        }

    }//GEN-LAST:event_m_jPrintActionPerformed
    /*
    Devolución de una factura
     */
    private void m_jRefundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_jRefundActionPerformed
        try {
            if (!verifyRefund(m_ticket.getId())) {
                String refund = getRefund(m_ticket.getId());
                JOptionPane.showMessageDialog(this,
                        "El comprobante ya está registrado en la devolución "
                        + refund,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            java.util.List aRefundLines = new ArrayList();

            for (int i = 0; i < m_ticket.getLinesCount(); i++) {
                TicketLineInfo newline = new TicketLineInfo(m_ticket.getLine(i));
                aRefundLines.add(newline);
            }

            m_ticketCopy = null;
            m_TicketsBagTicketBag.showRefund();
            m_panelticketedit.showRefundLines(aRefundLines);

            TicketInfo refundticket = new TicketInfo();
            refundticket.setTicketType(TicketInfo.RECEIPT_REFUND);
            refundticket.setTicketId(getNextTicketRefundIndex().intValue());
            refundticket.setCustomer(m_ticket.getCustomer());
            refundticket.setPayments(m_ticket.getPayments());
            refundticket.setLines(aRefundLines);

            TaxesLogic taxeslogic;
            DataLogicSales dlSales;
            dlSales = (DataLogicSales) m_App.getBean("com.openbravo.pos.forms.DataLogicSales");
            SentenceList senttax;
            senttax = dlSales.getTaxList();
            java.util.List<TaxInfo> taxlist = senttax.list();
            taxeslogic = new TaxesLogic(taxlist);

            taxeslogic.calculateTaxes(m_ticket);

            refundticket.setTaxes(m_ticket.getTaxes());

            System.out.println("Refund Cliente" + refundticket.getCustomerId());
            System.out.println(refundticket.getDate());
            System.out.println(m_ticket.getId());
            System.out.println("Money " + m_ticket.getActiveCash());

            saveRefund(refundticket, m_ticket);
//            m_panelticketedit.setActiveTicket(refundticket, null);
        } catch (BasicException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TaxesException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_m_jRefundActionPerformed

    private void saveRefund(TicketInfo refund, TicketInfo ticket) {
        saveRefundMaster(refund, ticket);
        saveRefundDetail(refund);
        saveRefundPayment(refund);
        saveRefundTaxes(refund);
    }

    private void saveRefundMaster(TicketInfo refund, TicketInfo ticket) {
        try {
            Connection connect = m_App.getSession().getConnection();

            //Funciona solo para MySQL
            PreparedStatement preparedStatementReceipt = connect.
                    prepareStatement("INSERT INTO RECEIPTS "
                            + "(ID, "
                            + "MONEY, "
                            + "DATENEW) "
                            + "VALUES(?, ?, sysdate())");

            preparedStatementReceipt.setString(1, refund.getId());
            preparedStatementReceipt.setString(2, ticket.getActiveCash());
            preparedStatementReceipt.execute();

            PreparedStatement preparedStatementTicket = connect.
                    prepareStatement("INSERT INTO TICKETS "
                            + "(ID, "
                            + "TICKETTYPE, "
                            + "TICKETID, "
                            + "PERSON, "
                            + "CUSTOMER) "
                            + "VALUES(?, ?, ?, ?, ?)");

            preparedStatementTicket.setString(1, refund.getId());
            preparedStatementTicket.setInt(2, 1);
            preparedStatementTicket.setInt(3, refund.getTicketId());
            preparedStatementTicket.setString(4, "0");
            preparedStatementTicket.setString(5, refund.getCustomerId());
            preparedStatementTicket.execute();

            PreparedStatement preparedStatementRefund = connect.
                    prepareStatement("INSERT INTO REFUNDS "
                            + "(ID, "
                            + "TICKET) "
                            + "VALUES(?, ?)");

            preparedStatementRefund.setString(1, refund.getId());
            preparedStatementRefund.setString(2, ticket.getId());
            preparedStatementRefund.execute();

            connect.close();
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }

    private void saveRefundDetail(TicketInfo refund) {
        try {
            Connection connect = m_App.getSession().getConnection();
            for (int i = 0; i < refund.getLinesCount(); i++) {
                TicketLineInfo detail = new TicketLineInfo(refund.getLine(i));
                PreparedStatement preparedStatementDetail = connect.
                        prepareStatement("INSERT INTO TICKETLINES "
                                + "(TICKET, "
                                + "LINE, "
                                + "PRODUCT, "
                                + "UNITS, "
                                + "PRICE, "
                                + "TAXID,"
                                + "ATTRIBUTES) "
                                + "VALUES(?, ?, ?, ?, ?, ?, ?)");

                preparedStatementDetail.setString(1, refund.getId());
                preparedStatementDetail.setInt(2, i);
                preparedStatementDetail.setString(3, detail.getProductID());
                preparedStatementDetail.setDouble(4, detail.getMultiply() * (-1));
                preparedStatementDetail.setDouble(5, detail.getPrice());
                preparedStatementDetail.setString(6, detail.getTaxInfo().getId());
                try {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    detail.getProperties().storeToXML(o, AppLocal.APP_NAME, "UTF-8");
                    preparedStatementDetail.setBytes(7, o.toByteArray());
                } catch (IOException e) {
                    preparedStatementDetail.setBytes(7, null);
                }
                preparedStatementDetail.execute();

                System.out.println(detail.getProductID() + " "
                        + detail.getProductName() + " "
                        + detail.getMultiply() + " "
                        + detail.getPrice() + " "
                        + detail.getTaxInfo().getId() + " "
                        + detail.getProperties());
            }
            connect.close();
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void saveRefundPayment(TicketInfo refund) {
        try {
            Connection connect = m_App.getSession().getConnection();
            for (PaymentInfo p : refund.getPayments()) {
                PreparedStatement preparedStatementPayment = connect.
                        prepareStatement("INSERT INTO PAYMENTS "
                                + "(ID, "
                                + "RECEIPT, "
                                + "PAYMENT, "
                                + "TOTAL) "
                                + "VALUES(?, ?, ?, ?)");

                preparedStatementPayment.setString(1, UUID.randomUUID().toString());
                preparedStatementPayment.setString(2, refund.getId());

                if (p.getName().equals("cash")) {
                    preparedStatementPayment.setString(3, "cashrefund");
                } else if (p.getName().equals("cheque")) {
                    preparedStatementPayment.setString(3, "chequerefund");
                } else {
                    preparedStatementPayment.setString(3, "cashrefund");
                }

                preparedStatementPayment.setDouble(4, (p.getTotal() * (-1)));
                preparedStatementPayment.execute();

            }
            connect.close();
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void saveRefundTaxes(TicketInfo refund) {
        try {
            Connection connect = m_App.getSession().getConnection();
            for (TicketTaxInfo tax : refund.getTaxes()) {
                PreparedStatement preparedStatementTax = connect.
                        prepareStatement("INSERT INTO TAXLINES "
                                + "(ID, "
                                + "RECEIPT, "
                                + "TAXID, "
                                + "BASE, "
                                + "AMOUNT) "
                                + "VALUES(?, ?, ?, ?, ?)");

                preparedStatementTax.setString(1, UUID.randomUUID().toString());
                preparedStatementTax.setString(2, refund.getId());
                preparedStatementTax.setString(3, tax.getTaxInfo().getId());
                preparedStatementTax.setDouble(4, (tax.getSubTotal() * (-1)));
                preparedStatementTax.setDouble(5, (tax.getTax() * (-1)));
                preparedStatementTax.execute();
            }
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
    Retorna la secuencia de las devoluciones
     */
    public final Integer getNextTicketRefundIndex() throws BasicException {
        return (Integer) m_App.getSession().DB.getSequenceSentence(m_App.getSession(), "TICKETSNUM_REFUND").find();
    }

    /*
    Si retorna true, se puede hacer una devolución. Si retorna false, no.
     */
    private Boolean verifyRefund(String ticketId) {
        try {
            Connection connect = m_App.getSession().getConnection();
            PreparedStatement preparedStatement = connect.
                    prepareStatement("SELECT COUNT(*) as count "
                            + "from REFUNDS where ticket = ?");
            preparedStatement.setString(1, ticketId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                if (resultSet.getInt("count") == 0) {
                    return true;
                } else {
                    return false;
                }
            }
            connect.close();
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return false;
    }

    /*
    Retorna el número de devolución en donde está el comprobante de venta.
     */
    private String getRefund(String ticketId) {
        String id = null;
        try {
            Connection connect = m_App.getSession().getConnection();
            PreparedStatement preparedStatement = connect.
                    prepareStatement("select t.TICKETID as id from REFUNDS r "
                            + "join TICKETS t "
                            + "on r.id = t.id where r.ticket = ?");
            preparedStatement.setString(1, ticketId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                id = resultSet.getString("id");
            }
            connect.close();
        } catch (SQLException ex) {
            Logger.getLogger(JTicketsBagTicket.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return id;
    }

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed

        readTicket(-1, jrbSales.isSelected() ? 0 : 1);

    }//GEN-LAST:event_jButton1ActionPerformed

    private void m_jKeysActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_jKeysActionPerformed

        readTicket(-1, jrbSales.isSelected() ? 0 : 1);

    }//GEN-LAST:event_m_jKeysActionPerformed

private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
    JTicketsFinder finder = JTicketsFinder.getReceiptFinder(this, m_dlSales, dlCustomers);
    finder.setVisible(true);
    FindTicketsInfo selectedTicket = finder.getSelectedCustomer();
    if (selectedTicket == null) {
        m_jTicketEditor.reset();
        m_jTicketEditor.activate();
    } else {
        readTicket(selectedTicket.getTicketId(), selectedTicket.getTicketType());
    }
}//GEN-LAST:event_jButton2ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JRadioButton jrbRefunds;
    private javax.swing.JRadioButton jrbSales;
    private javax.swing.JPanel m_jButtons;
    private javax.swing.JButton m_jEdit;
    private com.openbravo.editor.JEditorKeys m_jKeys;
    private javax.swing.JPanel m_jOptions;
    private javax.swing.JPanel m_jPanelTicket;
    private javax.swing.JButton m_jPrint;
    private javax.swing.JButton m_jRefund;
    private com.openbravo.editor.JEditorIntegerPositive m_jTicketEditor;
    private javax.swing.JLabel m_jTicketId;
    // End of variables declaration//GEN-END:variables

}
