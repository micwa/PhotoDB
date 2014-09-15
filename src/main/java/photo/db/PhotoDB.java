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
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    // Array storing column order, Hashmap storing column names & type
    private String[] columnNames;
    private HashMap<String, DataType> columnTypes;
	
    // Retrieved photos & properties
	private File[] currPhotos;
	private Properties[] currProps;
	private static final String TEMP_PATH = "temp135134";

	private final Logger log = Logger.getLogger(PhotoDB.class.getName());

    public enum DataType { INT, BOOLEAN, DOUBLE, LONG, 
                            STRING, DATE, TIME, BIN_STREAM }; 
	
	public PhotoDB(String hostname)
	{
		dbHostname = hostname;
        columnNames = new String[] { "index", "filename", "format", "description",
                            "size", "date", "image", "thumb" };
        columnTypes = new HashMap<String, DataType>();
        columnTypes.put(columnNames[0], DataType.INT);
        columnTypes.put(columnNames[1], DataType.STRING);
        columnTypes.put(columnNames[2], DataType.STRING);
        columnTypes.put(columnNames[3], DataType.STRING);
        columnTypes.put(columnNames[4], DataType.LONG);
        columnTypes.put(columnNames[5], DataType.DATE);
        columnTypes.put(columnNames[6], DataType.BIN_STREAM);
        columnTypes.put(columnNames[7], DataType.BIN_STREAM);
		
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
        {
			if (f[i].isFile())
			{
                Object[] data = new Object[columnNames.length];

				String filename = f[i].getName();							//Preparing info
				String format = filename.substring(filename.lastIndexOf(".") + 1, filename.length());
				long size = f[i].length();
                String description = "[none]";

                data[0] = i;
                data[1] = filename;
                data[2] = format;
                data[3] = description;
                data[4] = size;
                data[5] = new Date(f[i].lastModified());
                data[6] = f[i];
                data[7] = f[i];
				
				insertRow(i, data);
			}
        }
		log.info("END: FOLDER LOAD");
	}
	
	public void insertRow(int index, Object[] data)
	{
		String query = "INSERT INTO " + tableName + " VALUES (?";
        for (int i = 0; i < columnNames.length - 1; i++)
            query += ", ?";
        query += ")";

		PreparedStatement stmt = null;
		FileInputStream fis = null; 
		
		try {
            stmt = conn.prepareStatement(query);							//Executing query---

 			for (int i = 0; i < columnNames.length; i++)
            {
                switch (columnTypes.get(columnNames[i]))
                {
                    case INT:
                        stmt.setInt(i, (Integer) data[i]);
                        break;
                    case BOOLEAN:
                        stmt.setBoolean(i, (Boolean) data[i]);
                        break;
                    case LONG:
                        stmt.setLong(i, (Long) data[i]);
                        break;
                    case DOUBLE:
                        stmt.setDouble(i, (Double) data[i]);
                        break;
                    case STRING:
                        stmt.setString(i, (String) data[i]);
                        break;
                    case DATE:
                        stmt.setDate(i, (Date) data[i]);
                        break;
                    case TIME:
                        stmt.setTime(i, (Time) data[i]);
                        break;
                    case BIN_STREAM:
                        fis = new FileInputStream((File) data[i]);								//Inputting the blob

                        if (columnNames[i].toLowerCase().indexOf("thumb") > -1)
                        {
                            BufferedImage image = ImageIO.read(fis);						
                            BufferedImage buff = resizeImage(image, image.getWidth() * 64 / image.getHeight(), 64);
                            
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            ImageIO.write(buff,"jpg", os); 
                            InputStream in = new ByteArrayInputStream(os.toByteArray());
                            stmt.setBinaryStream(7, in);
                        }
                        else
                            stmt.setBinaryStream(i, fis, ((File) data[i]).length());
                        break;
                    default:
                        log.error("Data type not supported");
                        break;
                }
            }
            stmt.execute();													//---execution finished
            
            log.info("Row " + index + ": loaded");
		}
		catch (Exception e) {
			e.printStackTrace();
	        log.error("ERROR inserting row");
	    }
	}
	
	public File[] getRetrievedPhotos()										//Can only be called if retrievePhotos() has been called >=1 time
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
	
	public void setHostname(String hostname)
	{
		this.dbHostname = hostname;
	}
	
	public void setDBName(String dbName)
	{
		this.dbName = dbName;
	}
	
	public void setTableName(String tableName)
	{
		this.tableName = tableName;
	}
	
	public void setUser(String user)
	{
		this.user = user;
	}
	
	public void setPassword(String password)
	{
		this.password = password;
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
