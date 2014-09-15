package photo.db;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

public class PhotoDB
{
    // Variables required to connect to database
	private String dbURLStart = "jdbc:mysql://";
	private String dbHostname;
	private String dbName;
	private String tableName;
	private String user, password;
	private Connection conn;
	
    // Retrieved photos & properties
	private File[] currPhotos;
	private Properties[] currProps;
	private static final String TEMP_PATH = "~/temp";//"C:\\temp131536";

	private final Logger log = Logger.getLogger(PhotoDB.class.getName());
	
	public PhotoDB(String hostname)
	{
		dbHostname = hostname;
		
		File tempDir = new File(TEMP_PATH);
		if (!tempDir.exists())
			tempDir.mkdirs();
		
		log.info("PhotoDB constructed");
	}
	
	public void connect()
	{
	    conn = null;
	    Properties connectionProps = new Properties();
	    
	    try {
	    	conn = DriverManager.getConnection(dbURLStart + dbHostname + "/" + dbName, user, password);
	    	log.info("Connected to database");
	    }
	    catch (SQLException e){
	    	e.printStackTrace();
	    	log.error("Error connecting to database");
	    }
	}
	
	public void loadFolder(String folderPath)								//Inserts all images in folderPath to database
	{
		File[] f = new File(folderPath).listFiles();
		
		for (int i = 0; i < f.length; i++)
			if (f[i].isFile())
			{
				String filename = f[i].getName();							//Preparing info
				String format = filename.substring(filename.lastIndexOf(".") + 1, filename.length());
				long size = f[i].length();
				
				insertRow(0 + i, filename, format, "nothing here", size, f[i]);
			}
		log.info("END: FOLDER LOAD");
	}
	
	public void insertRow(int index, String filename, String format, String description, long size, File blob)
	{
		String query = "INSERT INTO " + tableName + " VALUES (?, ?, ?, ?, ?, ?, ?)";
		PreparedStatement stmt = null;
		FileInputStream fis = null; 
		
		try {
			stmt = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE filename=?");
			stmt.setString(1, filename);
			stmt.execute();
			ResultSet rs = stmt.getResultSet();
			if (!rs.next())
			{
				stmt.close();
			    stmt = conn.prepareStatement(query);							//Executing query---
			    
			    stmt.setInt(1, index);
			    stmt.setString(2, filename);
			    stmt.setString(3, format);
			    stmt.setString(4,  "nothing here");
			    stmt.setLong(5, size);			   
	
			    fis = new FileInputStream(blob);								//Inputting the blob
			    stmt.setBinaryStream(6, fis, size);
			    
			    fis = new FileInputStream(blob);								//Assuming stream can't be reused
			    BufferedImage image = ImageIO.read(fis);						
			    BufferedImage buff = resizeImage(image, image.getWidth() * 64 / image.getHeight(), 64);
			    
			    ByteArrayOutputStream os = new ByteArrayOutputStream();
			    ImageIO.write(buff,"jpg", os); 
			    InputStream in = new ByteArrayInputStream(os.toByteArray());
			    stmt.setBinaryStream(7, in);
			    
			    stmt.execute();													//---execution finished
				
				log.info(filename + " loaded");
			}
			else
				log.info(filename + " already exists in database");
		}
		catch (Exception e) {
			e.printStackTrace();
	        log.error("ERROR inserting row");
	    } finally {
	    	try { if (stmt != null) stmt.close();} catch (SQLException e) {}
	    	try { if (fis != null) fis.close();} catch (IOException e) {}
		}
	}
	
	public File[] getRetrievedPhotos()												//Can only be called if retrievePhotos() has been called >=1 time
	{
		return currPhotos;
	}
	
	public Properties[] getRetrievedPhotoProperties()
	{
		return currProps;
	}
	
	public Image getSpecificPhoto(int index)								//Use index from properties to get a specific image
	{
		Image image = null;
		Statement stmt = null;
		
		try {
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE `index`=" + index);
			
			rs.next();														//Reading the image
			InputStream in = rs.getBinaryStream(6);	
			image = ImageIO.read(in);	
		} catch (Exception e ) {
	    	e.printStackTrace();
	        log.error("ERROR retrieving photo with index " + index);
	    } finally {
	    	try { if (stmt != null) stmt.close();} catch (SQLException e) {}
	    }
		return image;
	}
	
	public Image[] getPhotoThumbnails()
	{
		Image[] thumbs = null;
		Statement stmt = null;
		
		try {
			stmt = conn.createStatement();
			ResultSet rows = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
			rows.next();
			int numRows = rows.getInt(1);
			thumbs = new Image[numRows];
			
			for (int i = 0; i < numRows; i++)
			{
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE `index`=" + i);
				
				rs.next();														//Reading the image
				InputStream in = rs.getBinaryStream(7);	
				thumbs[i] = ImageIO.read(in);	
			}
		} catch (Exception e ) {
	    	e.printStackTrace();
	        log.error("ERROR retrieving thumbnails");
	    } finally {
	    	try { if (stmt != null) stmt.close();} catch (SQLException e) {}
	    }
		
		return thumbs;
	}
	
	public void retrievePhotos()											//Retrieves photos from database and writes them to a temp directory
	{
		Statement stmt = null;
		ArrayList<File> paths = new ArrayList<File>();
		ArrayList<Properties> props = new ArrayList<Properties>();
		
		try {
			stmt = conn.createStatement();
	        ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);						//Getting rows from table
	        
	        while (rs.next()) {
	        	int index = rs.getInt(1);									//Getting properties info
	        	String filename = rs.getString(2);
	        	String format = rs.getString(3);
	        	String descrip = rs.getString(4);
	        	int size = rs.getInt(5);
	        		        	
	        	File tempFile = new File(TEMP_PATH + "\\" + filename);
	        	
	        	paths.add(tempFile);										//Add even if file exists already
        		Properties tempProp = new Properties();
        		tempProp.setProperty("index", index + "");
        		tempProp.setProperty("filename", filename);
        		tempProp.setProperty("format",  format);
        		tempProp.setProperty("description", descrip);
        		tempProp.setProperty("size",  size + "");
        		props.add(tempProp);
	        	
	        	if (!tempFile.exists())										//In case temp files did not delete
	        	{
	        		InputStream in = rs.getBinaryStream(6);
	        		OutputStream os = null;
	        		
	        		try {
	        			os = new FileOutputStream(tempFile);				//Writing the image to disk
	        			int c = 0;
	        			while ((c = in.read()) != -1)
	        				os.write(c);
	        			
	        			log.info(filename + " written to disk");
	        		} catch (IOException e) {e.printStackTrace();}
	        		finally {
	        			try { if (in != null) in.close();} catch (IOException e) {}
	        			try { if (os != null) os.close();} catch (IOException e) {}
	        		}
	        	}
	        }
	    } catch (SQLException e ) {
	    	e.printStackTrace();
	        log.error("ERROR retrieving");
	    } finally { 
	    	try { if (stmt != null) stmt.close();} catch (SQLException e) {}
	    }
		currPhotos = new File[paths.size()];
		currProps = new Properties[props.size()];
		
		for (int i = 0; i < currPhotos.length; i++)
		{
			currPhotos[i] = paths.get(i);
			currProps[i] = props.get(i);
		}
	}
	
	public void retrievePhotoPropertiesOnly()									//Retrieves photo properties from database
	{
		Statement stmt = null;
		ArrayList<Properties> props = new ArrayList<Properties>();
		
		try {
			stmt = conn.createStatement();
	        ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);						//Getting rows from table
	        
	        while (rs.next()) {
	        	int index = rs.getInt(1);									//Getting properties info
	        	String filename = rs.getString(2);
	        	String format = rs.getString(3);
	        	String descrip = rs.getString(4);
	        	int size = rs.getInt(5);
	        		        	
        		Properties tempProp = new Properties();
        		tempProp.setProperty("index", index + "");
        		tempProp.setProperty("filename", filename);
        		tempProp.setProperty("format",  format);
        		tempProp.setProperty("description", descrip);
        		tempProp.setProperty("size",  size + "");
        		props.add(tempProp);
	        }
	    } catch (SQLException e ) {
	    	e.printStackTrace();
	        log.error("ERROR retrieving");
	    } finally {
	    	try { if (stmt != null) stmt.close();} catch (SQLException e) {}
	    }
		currProps = new Properties[props.size()];
		
		for (int i = 0; i < currProps.length; i++)
			currProps[i] = props.get(i);
	}
	
	public void deleteTempFiles()
	{
		File tempDir = new File(TEMP_PATH);
		if (tempDir.exists())
		{
			File[] files = tempDir.listFiles();
			
			for (int i = 0; i < files.length; i++)
				files[i].delete();
			tempDir.delete();
			log.info("Deleted temp files");
		}
	}
	
	public void setHostname(String host)
	{
		dbHostname = host;
	}
	
	public void setDBName(String db)
	{
		dbName = db;
	}
	
	public void setTableName(String table)
	{
		tableName = table;
	}
	
	public void setUser(String user)
	{
		this.user = user;
	}
	
	public void setPassword(String passwd)
	{
		this.password = passwd;
	}
	
	private BufferedImage resizeImage(Image img, int width, int height)
	{
		BufferedImage buff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = buff.createGraphics();
		g2d.drawImage(img, 0, 0, width, height, null);
		g2d.dispose();
		
		return buff;
	}
}
