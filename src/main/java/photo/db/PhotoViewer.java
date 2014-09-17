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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

import org.apache.log4j.Logger;

public class PhotoViewer extends JFrame
{
	private PhotoPanel photoPanel;
	private JMenuBar menuBar;
	private JMenu fileMenu, editMenu;
	private JMenuItem connectItem, uploadItem, settItem, disconnectItem, exitItem;
	
	private SettingsDialog settingsDialog;
	
	private final Logger log = Logger.getLogger(PhotoDB.class.getName());
	
	public PhotoViewer(PhotoDB db)
	{
		super();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) { e.printStackTrace(); }
		
		photoPanel = new PhotoPanel(db);									//Pass the db on
		
		initMenu();
		add(photoPanel);
		
		settingsDialog = SettingsDialog.getDialog();						//settingsDialog dialog
		settingsDialog.setPhotoViewer(this);
		settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		settingsDialog.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e)
			{
				settingsDialog.dialogCancel();								//Don't save changes if closed using "x"
				updateSettingsFromDialog();
				log.info("Dialog closed");
			}
		});
		settingsDialog.setVisible(false);
		
		updateSettingsFromDialog();
		
		log.info("PhotoViewer constructed");
	}
	
	private void initMenu()
	{
		menuBar = new JMenuBar();											//Menus
		fileMenu = new JMenu("File");
		editMenu = new JMenu("Edit");
		
		connectItem = new JMenuItem("Connect to DB");						//File menu
		uploadItem = new JMenuItem("Upload folder...");
		disconnectItem = new JMenuItem("Disconnect");
		exitItem = new JMenuItem("Exit");
		fileMenu.add(connectItem);
		fileMenu.add(disconnectItem);
		fileMenu.add(uploadItem);
		fileMenu.add(exitItem);
		fileMenu.insertSeparator(2);
		
		settItem = new JMenuItem("Settings");								//Edit menu
		editMenu.add(settItem);
		
		ActionListener al = new ButtonListener();
		connectItem.addActionListener(al);
		uploadItem.addActionListener(al);
		disconnectItem.addActionListener(al);
		exitItem.addActionListener(al);
		settItem.addActionListener(al);
		
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		
		setJMenuBar(menuBar);
		
		log.info("Menu initialized");
	}
	
	// Override dispose() so that PhotoPanel can be disposed of also,
	// mostly to delete the temp files and close the connection
	public void dispose()
	{
		photoPanel.dispose();												//This must go first
		super.dispose();
	}
	
	// Gets the inputed settings from the settings dialog and calls PhotoPanel
	// to update them in PhotoDB
	public void updateSettingsFromDialog()
	{
		String host = settingsDialog.getHostname();
		String dbName = settingsDialog.getDBName();
		String tableName = settingsDialog.getTableName();
		String user = settingsDialog.getUsername();
		String passwd = settingsDialog.getPassword();
		
		photoPanel.updateDBSettings(host, dbName, tableName, user, passwd);
	}
	
	private class ButtonListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			if (e.getSource() == connectItem)
				photoPanel.connectToDB();
			else if (e.getSource() == uploadItem)
				photoPanel.uploadPhotosIntoDB();
			else if (e.getSource() == disconnectItem)
				photoPanel.disconnectFromDB();
			else if (e.getSource() == settItem)
			{
				settingsDialog.setLocationRelativeTo(PhotoViewer.this);		//Doesn't work if put in constructor
				settingsDialog.setVisible(true);
			}
			else if (e.getSource() == exitItem)
				PhotoViewer.this.dispose();
		}
	}
}
