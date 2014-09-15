package photo.db;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.log4j.Logger;

public class PhotoPanel extends JPanel
{
	private PhotoDB db;
	
	private JPanel south;													//South items
	private JButton left, right;
	private JPanel thumbPanel; 
	private Image[] thumbs;
	private JButton[] thumbButtons;
	private JScrollPane thumbScroll, propScroll;
	private JLabel props;
	
	private Image currPhoto;
	private int currIndex = -1;
	
	private final Logger log = Logger.getLogger(PhotoPanel.class.getName());
	
	public PhotoPanel(PhotoDB db)
	{
		this.db = db;
		
		setBackground(Color.WHITE);
		setLayout(new BorderLayout());
		
		initSouthPanel();

		log.info("PhotoPanel constructed");
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
		
		//log.info("South panel initialized");
	}
	
	public void connectToDB()
	{
		db.connect();														//Connect and initialize the thumbnail pane with photos
		initThumbPane();
		
		left.setEnabled(true);												//Show image view and view the first photo
		right.setEnabled(true);
		showPhoto(0);
	}
	
	private void initThumbPane()											//Load selection list
	{
		db.retrievePhotoPropertiesOnly();										//Getting image thumbnails
		thumbs = db.getPhotoThumbnails();
		thumbButtons = new JButton[thumbs.length];
		
		thumbPanel = new JPanel();											//Setting up pane on left
		thumbPanel.setBackground(Color.WHITE);
		thumbPanel.setLayout(new BoxLayout(thumbPanel, BoxLayout.Y_AXIS));
		thumbPanel.add(Box.createRigidArea(new Dimension(0, 5)));			//Initial space
		
		thumbScroll = new JScrollPane(thumbPanel);
		thumbScroll.setBackground(Color.WHITE);
		thumbScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(thumbScroll, BorderLayout.WEST);
		
		ActionListener al = new ButtonListener();
		for (int i = 0; i < thumbs.length; i++)
		{
			thumbButtons[i] = new JButton(new ImageIcon(thumbs[i]));
			thumbButtons[i].setPreferredSize(new Dimension(100, 64));
			thumbButtons[i].setBorder(BorderFactory.createEmptyBorder());
			thumbButtons[i].setContentAreaFilled(false);
			thumbButtons[i].setAlignmentX(Component.CENTER_ALIGNMENT);
			thumbButtons[i].addActionListener(al);
			
			thumbPanel.add(thumbButtons[i]);
			thumbPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		}
		
		thumbPanel.revalidate();
		thumbPanel.repaint();
		revalidate();
		repaint();
		
		log.info("Loaded thumbnail pane");
	}	
	
	public void uploadPhotosIntoDB()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int value = chooser.showDialog(this, "Upload");
		if (value == JFileChooser.APPROVE_OPTION)
		{
			File f = chooser.getSelectedFile();
			try{ 
				db.loadFolder(f.getCanonicalPath()); 
				log.info(f.getCanonicalPath() + " folder loaded");
			} catch (IOException e) {e.printStackTrace();}
			initThumbPane();											//Update list of photos
		}
		else if (value == JFileChooser.CANCEL_OPTION)
			log.info("Upload canceled");
	}
	
	public void showPhoto(int index)										//Sets current index to index, gets the photo from the database, calls repaint()
	{		
		if (thumbButtons.length == 0 || index == currIndex)
			return;
		
		int prevIndex = currIndex;
		currIndex = index;
		if (currIndex >= thumbButtons.length)								//No more next/prevPhoto()
			currIndex = 0;
		if (currIndex < 0)
			currIndex = thumbButtons.length - 1;
		
		Border border = new LineBorder(Color.BLUE, 3);						//Remove last border and add to newly clicked
		if (prevIndex != -1)
			thumbButtons[prevIndex].setBorder(BorderFactory.createEmptyBorder());
		thumbButtons[currIndex].setBorder(border);
		
		currPhoto = db.getSpecificPhoto(currIndex);
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
	}
	
	public void updatePhotoProperties()
	{
		Properties[] p = db.getRetrievedPhotoProperties();
		String index = p[currIndex].getProperty("index");
		String name = p[currIndex].getProperty("filename");
		String format = p[currIndex].getProperty("format");
		String descrip = p[currIndex].getProperty("description");
		String size = p[currIndex].getProperty("size");
		
		props.setText("<html><pre>Properties: " + "<br><br>"
						+ "Index: " + index + "\t\tFilename: " + name + "<br>Format: " + format
						 + "\t\tSize: " + size + " bytes<br>Description: " + descrip + "</pre></html>");
		
		log.info("Properties updated");
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
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
			log.info("Image displayed at index " + currIndex);
		}
	}
	
	private class ButtonListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			if (e.getSource() == left)
				showPhoto(currIndex - 1);
			else if (e.getSource() == right)
				showPhoto(currIndex + 1);
			else if (e.getSource() instanceof JButton)
			{
				for (int i = 0; i < thumbButtons.length; i++)
					if (e.getSource() == thumbButtons[i])
					{
						showPhoto(i);
						return;
					}
			}
		}
	}
}
