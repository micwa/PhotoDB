/**
 * This file is part of PhotoDB - MySQL client/GUI for accessing photo databases
 * 
 * Copyright (C) 2014 by Michael Wang
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *   
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package photo.db;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.SwingConstants;
import javax.swing.JLabel;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JTextField;
import javax.swing.JPasswordField;

import org.apache.log4j.Logger;

public class SettingsDialog extends JDialog
{
	private final JPanel contentPanel = new JPanel();
	private JTextField txtHostname;
	private JTextField txtDBName;
	private JTextField txtUsername;
	private JTextField txtTableName;
	private JPasswordField pwdField;
	private String[] prevValues;
	
	private PhotoViewer viewer;
	private static SettingsDialog self = new SettingsDialog();
	private static final Logger log = Logger.getLogger(SettingsDialog.class.getName());
	
	/**
	 * Create the dialog.
	 */
	private SettingsDialog()
	{
		setBounds(300, 300, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new GridLayout(5, 2, 10, 10));
		{
			JLabel lblNewLabel_1 = new JLabel("URL:port");
			lblNewLabel_1.setHorizontalAlignment(SwingConstants.CENTER);
			contentPanel.add(lblNewLabel_1);
		}
		{
			txtHostname = new JTextField();
			txtHostname.setText("localhost:3306");
			contentPanel.add(txtHostname);
			txtHostname.setColumns(10);
		}
		{
			JLabel lblNewLabel_2 = new JLabel("Database name:");
			lblNewLabel_2.setHorizontalAlignment(SwingConstants.CENTER);
			contentPanel.add(lblNewLabel_2);
		}
		{
			txtDBName = new JTextField();
			txtDBName.setText("media_db");
			contentPanel.add(txtDBName);
			txtDBName.setColumns(10);
		}
		{
			JLabel lblNewLabel_3 = new JLabel("Table name:");
			lblNewLabel_3.setHorizontalAlignment(SwingConstants.CENTER);
			contentPanel.add(lblNewLabel_3);
		}
		{
			txtTableName = new JTextField();
			txtTableName.setText("photo_table");
			contentPanel.add(txtTableName);
			txtTableName.setColumns(10);
		}
		{
			JLabel lblNewLabel = new JLabel("Username:");
			lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
			contentPanel.add(lblNewLabel);
		}
		{
			txtUsername = new JTextField();
			txtUsername.setText("root");
			txtUsername.setToolTipText("");
			contentPanel.add(txtUsername);
			txtUsername.setColumns(6);
		}
		{
			JLabel lblNed = new JLabel("Password:");
			lblNed.setHorizontalAlignment(SwingConstants.CENTER);
			contentPanel.add(lblNed);
		}
		{
			pwdField = new JPasswordField();
			pwdField.setText("");
			contentPanel.add(pwdField);
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout());
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			ActionListener al = new MyActionListener();
			{
				JButton okButton = new JButton("OK");
				okButton.setActionCommand("OK");
				okButton.addActionListener(al);
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.setActionCommand("Cancel");
				cancelButton.addActionListener(al);
				buttonPane.add(cancelButton);
			}
		}
		prevValues = new String[5];
		storePrevValues();
	}
	
	public static SettingsDialog getDialog()
	{
		return self;
	}
	
	public void setPhotoViewer(PhotoViewer viewer)
	{
		this.viewer = viewer;
	}
	
	public String getHostname()
	{
		return txtHostname.getText();
	}
	
	public String getDBName()
	{
		return txtDBName.getText();
	}
	
	public String getTableName()
	{
		return txtTableName.getText();
	}
	
	public String getUsername()
	{
		return txtUsername.getText();
	}
	
	public String getPassword()
	{
		return new String(pwdField.getPassword());
	}
	
	public void setHostname(String host)
	{
		txtHostname.setText(host);
	}
	
	public void setDBName(String db)
	{
		txtDBName.setText(db);
	}
	
	public void setTableName(String table)
	{
		txtTableName.setText(table);
	}
	
	public void setUsername(String user)
	{
		txtUsername.setText(user);
	}
	
	public void setPassword(String passwd)
	{
		pwdField.setText(passwd);
	}
	
	public void dialogUpdate()												//Closes dialog and saves changes
	{
		storePrevValues();
		setVisible(false);
		log.info("Dialog oked");
	}
	
	public void dialogCancel()												//Closes dialog, does not save changes
	{
		revertToPrevValues();
		setVisible(false);
		log.info("Dialog canceled");
	}
	
	private void storePrevValues()
	{
		prevValues[0] = getHostname();
		prevValues[1] = getDBName();
		prevValues[2] = getTableName();
		prevValues[3] = getUsername();
		prevValues[4] = getPassword();
	}
	
	private void revertToPrevValues()
	{
		setHostname(prevValues[0]);
		setDBName(prevValues[1]);
		setTableName(prevValues[2]);
		setUsername(prevValues[3]);
		setPassword(prevValues[4]);
	}
	
	private class MyActionListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			if (e.getActionCommand().equals("OK"))
			{
				dialogUpdate();
				viewer.updateSettingsFromDialog();
			}
			else if (e.getActionCommand().equals("Cancel"))
				dialogCancel();
		}
	}
}
