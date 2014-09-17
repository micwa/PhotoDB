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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

import org.apache.log4j.Logger;

public class PhotoPanel extends JPanel
{
	private PhotoDB db;
	
	// South panel
	private JPanel south;	
	private JButton left, right;
	private JLabel props;
	private JScrollPane propScroll;
	
	// Thumbpane
	private JPanel thumbPanel; 
	private Image[] thumbs;
	private JButton[] thumbButtons;
	private JScrollPane thumbScroll;
	
	// Stores all unique keys for the photos
	private Object[] photoKeys;
	// The current photo, and the current index for the thumbnail array
	private Image currPhoto;
	private int currIndex = -1;
	
	// Whether PhotoPanel is connected to a database
	private boolean connected;
	
	// All properties for the photo
	private Properties[] photoProps;
	private final int NUM_PROPS = 6;
	// The same as default - just testing it out
	private final String[] COL_NAMES = { "INDEX", "FILENAME", "FORMAT", "DESCRIPTION",
        						"SIZE", "DATE", "IMAGE", "THUMB" };
	private final HashMap<String, DataType> COL_TYPES;

    {
        COL_TYPES = new HashMap<String, DataType>();
        COL_TYPES.put(COL_NAMES[0], DataType.INT);
        COL_TYPES.put(COL_NAMES[1], DataType.STRING);
        COL_TYPES.put(COL_NAMES[2], DataType.STRING);
        COL_TYPES.put(COL_NAMES[3], DataType.STRING);
        COL_TYPES.put(COL_NAMES[4], DataType.LONG);
        COL_TYPES.put(COL_NAMES[5], DataType.DATE);
        COL_TYPES.put(COL_NAMES[6], DataType.BIN_STREAM);
        COL_TYPES.put(COL_NAMES[7], DataType.BIN_STREAM);
    }
	
	private final Logger log = Logger.getLogger(PhotoPanel.class.getName());
	
	/**
	 * Initializes a new instance of <code>PhotoPanel</code> and sets the
	 * column names and types for PhotoDB. The layout is set to BorderLayout
	 * and the southern JPanel is initialized.
	 * 
	 * @param db The <code>PhotoDB</code> to connect to and display images from
	 */
	public PhotoPanel(PhotoDB db)
	{
		this.db = db;
		db.setColumnNames(COL_NAMES);
		db.setColumnTypes(COL_TYPES);
		db.setUniqueKey(1);													//Let the unique key be the filename
		connected = false;
		
		setBackground(Color.WHITE);
		setLayout(new BorderLayout());
		
		initSouthPanel();

		log.info("PhotoPanel constructed");
	}
	
	/**
	 * Attempts to connect to the database, and then loads up the thumbnail
	 * pane with whatever thumbnails it retrieves from the database, and
	 * re-retrieves the properties as well. Will display an error and return
	 * false if the attempt to connect fails.
	 * 
	 * @return <code>true</code> if a connection is established, <code>false</code> if not
	 */
	public boolean connectToDB()
	{
		try {
			db.connect();														//Connect and initialize the thumbnail pane with photos
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "Error connecting to database. Check your settings.",
											"Error", JOptionPane.ERROR_MESSAGE);
			log.error("Error connecting to database");
			return false;
		}
		retrievePropsFromDB();
		initThumbPane();
		connected = true;
		
		left.setEnabled(true);												//Show image view and view the first photo
		right.setEnabled(true);
		return true;
	}
	
	/**
	 * Disconnects from the database and disables all functions that require
	 * database access. Will display an error and return false if the attempt
	 * to disconnect fails.
	 * 
	 * @return <code>true</code> if the current connection is terminated
	 * successfully, <code>false</code> if not
	 */
	public boolean disconnectFromDB()
	{
		try {
			db.disconnect();
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "Error disconnecting from database.",
											"Error", JOptionPane.ERROR_MESSAGE);
			log.error("Error connecting to database");
			return false;
		}
		
		connected = false;
		left.setEnabled(false);
		right.setEnabled(false);
		JOptionPane.showMessageDialog(this, "Successfully disconnected from database");
		return true;
	}
	
	/**
	 * Disconnects from the database and deletes all temp files.
	 * It is not necessary to call this method unless there is no longer the need to
	 * call connectToDB() on this PhotoPanel anymore.
	 */
	public void dispose()
	{
		disconnectFromDB();
		db.deleteCachedFiles();
	}
	
	/**
	 * Upload photos from a specific folder (selected from the dialog) into the database. 
	 */
	public void uploadPhotosIntoDB()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setMultiSelectionEnabled(true);
		int value = chooser.showDialog(this, "Upload");
		if (value == JFileChooser.APPROVE_OPTION)
		{
			File[] f = chooser.getSelectedFiles();
			System.out.println(f[0].isFile() + ", " + f[0].getPath());
			for (int i = 0; i < f.length; i++)
			{
				if (f[i].isFile())
					loadFile(f[i]);
				else if (f[i].isDirectory())
					loadFolder(f[i]);
				else
					log.error("What else can this be?!?!");
			}
			new Thread(new Runnable() {
				public void run() 
				{
					initThumbPane();										//Update list of photos asynchronously
				}
			}).start();
		}
		else if (value == JFileChooser.CANCEL_OPTION)
			log.info("Upload canceled");
	}
	
	/**
	 * Sets the current photo to the (index+1)th photo in the database;
	 * draws a border around that photo's thumbnail; and calls repaint().
	 * If there is no connection to the database, this method will still
	 * paint thumbnail borders but always display the last obtained photo.
	 *
	 * @pre A photo at row <code>index+1</code> must exist in the database
	 * (or else there will be an ArrayOutOfBoundsException)
	 * @param index The index of the photo (per photoKeys) to show
	 */
	public void showPhoto(int index)				
	{		
		// No need to continue if same picture is clicked on twice
		if (index == currIndex)
			return;
		
		int prevIndex = currIndex;
		currIndex = index;
		
		// Wrap around if past the end/start
		if (currIndex >= thumbButtons.length)	
			currIndex = 0;
		if (currIndex < 0)
			currIndex = thumbButtons.length - 1;
		
		// Remove last border if prevIndex is valid
		if (prevIndex != -1)
			thumbButtons[prevIndex].setBorder(BorderFactory.createEmptyBorder());
		// Add new border
		Border border = new LineBorder(Color.BLUE, 3);
		thumbButtons[currIndex].setBorder(border);
		
		// Since the photo in the thumbnail array corresponds to the unique
		// key in the primary keys array, call getSpecificPhoto with that key
		// by tracking the current index of the thumbnail array.
		if (connected)
			currPhoto = db.getSpecificPhoto(photoKeys[currIndex]);
		updatePhotoProperties();
		repaint();
	}
	
	public void updateDBSettings(String host, String dbName, String tableName, String user, String password)
	{
		db.setHostname(host);
		db.setDBName(dbName);
		db.setTableName(tableName);
		db.setUser(user);
		db.setPassword(password);
		
		log.info("DB settings updated");
	}

	/**
	 * Whenever the database is updated (or if properties have not yet been
	 * retrieved), call this method to update the properties label. If for some
	 * reason there is an SQLException retrieving the properties, an error
	 * dialog pops up.
	 */
	public void retrievePropsFromDB()
	{
		try {
			db.retrievePhotoPropertiesOnly();
		} catch (SQLException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error retrieving properties from database",
											"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		photoProps = db.getRetrievedPhotoProperties();
	}
	
	/**
	 * This method updates the properties panel (south) to display the
	 * properties of the currently displayed image. All properties are displayed
	 * by cycling through them with all the column names, and thus they
	 * will appear in that order.
	 * 
	 * Note: retrievePropsFromDB() should be called before this to update
	 * PhotoPanel's properties or else they will not be consistent with the
	 * thumbnails. This method, besides checking for null, has no way of
	 * determining whether current properties are up-to-date or not.
	 */
	public void updatePhotoProperties()
	{
		// If there are no properties stored, return (should only be possible if
		// retrievePhotosFromDB() encountered an exception)
		// Also disallow viewing properties if disconnected (despite being cached)
		if (photoProps == null || !connected)
			return;
		
		Properties currProp = photoProps[currIndex];
		String[] values = new String[NUM_PROPS];
		
		int i = 0;
		// Loop through each column name
		for (String s : COL_NAMES)
			if (currProp.getProperty(s) != null)							//Store the property if it isn't null
				values[i++] = currProp.getProperty(s);
		
		// Hard-coded properties format
		props.setText("<html><pre><b>Properties:</b> " + "<br>" + "Index: " + values[0] + "\t\tFilename: "
						+ values[1] + "<br>Format: " + values[2] + "\t\tSize: " + values[4] + " bytes<br>Date: "
						+ values[5] + "\tDescription: " + values[3] + "</pre></html>");
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		// Don't bother if currPhoto == null (should only be possible after first initialization)
		if (currPhoto == null)
			return;
		
		if (currIndex != -1)
		{			
			//Drawing the image
			int w = getWidth() - thumbScroll.getWidth() - 10, h = getHeight() - south.getHeight() - 10;		//Available width/height
			int imageW = currPhoto.getWidth(this), imageH = currPhoto.getHeight(this);
			int width, height;
			if (imageH * w / imageW > h)
			{
				height = h;
				width = imageW * h / imageH;
			}
			else
			{
				height = imageH * w / imageW;
				width = w;
			}
			g.drawImage(currPhoto, thumbScroll.getWidth() + (w + 10) / 2 - width / 2, (h + 10) / 2 - height / 2, width, height, this);
		}
	}
	
	private void initSouthPanel()
	{
		left = new JButton("Prev");											//Setting up buttons, left/right disabled by default
		right = new JButton("Next");
		left.setEnabled(false);
		right.setEnabled(false);
		
		ActionListener al = new ButtonListener();
		left.addActionListener(al);
		right.addActionListener(al);
		
		props = new JLabel("Properties:");
		props.setBackground(Color.WHITE);
		propScroll = new JScrollPane(props);						
		propScroll.setBackground(Color.WHITE);
		
		south = new JPanel();
		south.add(left);
		south.add(propScroll);
		south.add(right);
		add(south, BorderLayout.SOUTH);
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e)
			{
				if (thumbScroll != null) thumbScroll.setPreferredSize(new Dimension(100, getHeight() - propScroll.getHeight()));
				if (propScroll != null) propScroll.setPreferredSize(new Dimension(getWidth() - right.getWidth() - left.getWidth() - 20, 95));
				south.revalidate();
				south.repaint();
				repaint();
			}
		});
	}
	
	/**
	 * Whenever the database is updated (or the thumbnail pane has not yet
	 * been initialized), call this method to update the thumbnail pane.
	 *
	 * First, this method retrieves the thumbnails (no properties) and the
	 * unique keys. Then it creates the JPanel that will house the
	 * JScrollPane, creates it (scroll pane), and adds the the buttons
	 * made from the thumbnails one by one to the JPanel.
	 */
	private void initThumbPane()	
	{							
		thumbs = db.getPhotoThumbnails();									//Getting image thumbnails
		photoKeys = db.getAllUniqueKeys();
		thumbButtons = new JButton[thumbs.length];

		thumbPanel = new JPanel();											//Setting up pane on left
		thumbPanel.setBackground(Color.WHITE);
		thumbPanel.setLayout(new BoxLayout(thumbPanel, BoxLayout.Y_AXIS));
		thumbPanel.add(Box.createRigidArea(new Dimension(0, 5)));			//Initial space
		
		if (thumbScroll != null)											//So there's no overlap
			this.remove(thumbScroll);
		thumbScroll = new JScrollPane(thumbPanel);							//ScrollPane
		thumbScroll.setBackground(Color.WHITE);
		thumbScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);		//Fixes alignment problems... thank god
		thumbScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(thumbScroll, BorderLayout.WEST);

		ActionListener al = new ButtonListener();
		for (int i = 0; i < thumbs.length; i++)
		{
			thumbButtons[i] = new JButton(new ImageIcon(thumbs[i]));
			thumbButtons[i].setPreferredSize(new Dimension(100, 64));
			thumbButtons[i].setBorder(BorderFactory.createEmptyBorder());
			thumbButtons[i].setContentAreaFilled(false);					//Make the icon the only button
			thumbButtons[i].setAlignmentX(Component.CENTER_ALIGNMENT);
			thumbButtons[i].addActionListener(al);

			thumbPanel.add(thumbButtons[i]);
			thumbPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		}

		// By default, show the first photo (if there is any)
		if (thumbButtons.length != 0)
			showPhoto(0);

		thumbPanel.revalidate();
		thumbPanel.repaint();
		revalidate();
		repaint();

		log.info("Loaded thumbnail pane");
	}
	
	/**
	 * Moved here from PhotoDB, since each PhotoDB client will likely upload
	 * photos differently.
     * 
	 * @param folderPath The java.io.File that represents the folder, the photos
	 * in which will be uploaded 
	 */
	private void loadFolder(File folder)
	{
		// Does NOT load recursively - only files in this folder
		File[] f = new File(folder.getPath()).listFiles();
		
		for (int i = 0; i < f.length; i++)
			if (f[i].isFile())
                loadFile(f[i]);

		log.info("END: FOLDER LOAD");
	}
	
	/**
	 * Uploads <code>file</code> into the database if it does not exist already.
	 * 
	 * @param file The java.io.File that represents the file being uploaded
	 */
	private void loadFile(File file)
	{
		Object[] data = new Object[COL_NAMES.length];

		// Preparing data
		String filename = file.getName();
		String format = filename.substring(filename.lastIndexOf(".") + 1, filename.length()).toUpperCase();
		long size = file.length();
		String description = "[none]";
		
		data[0] = filename.hashCode();
		data[1] = filename;
		data[2] = format;
		data[3] = description;
		data[4] = size;
		data[5] = new Date(file.lastModified());
		data[6] = file;
		data[7] = file;

		db.insertRow(data);
	}
	
	private class ButtonListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			// "Decrement" currIndex - the real assignment happens in showPhoto() itself
			if (e.getSource() == left)
				showPhoto(currIndex - 1);
			else if (e.getSource() == right)
				showPhoto(currIndex + 1);
			else if (e.getSource() instanceof JButton)
			{
				for (int i = 0; i < thumbButtons.length; i++)
				{
					if (e.getSource() == thumbButtons[i])
					{
						showPhoto(i);
						return;
					}
				}
			}
		}
	}
}
