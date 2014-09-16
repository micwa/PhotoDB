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
import java.sql.Types;
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

    // Array that stores column order, Hashmap that stores column names & type
    private String[] columnNames;
    private HashMap<String, DataType> columnTypes;
	
    // Retrieved photos & properties
	private File[] currPhotos;
	private Properties[] currProps;
	private static final String TEMP_PATH = "temp135134";

	private final Logger log = Logger.getLogger(PhotoDB.class.getName());
	
	// The column of the primary key, to identify each row entry
	private int primaryKey;
	
    // Private default values for the table schema
    private static final String[] DEFAULT_COL_NAMES = { "INDEX", "FILENAME", "FORMAT", "DESCRIPTION",
                            "SIZE", "DATE", "IMAGE", "THUMB" };
    private static final HashMap<String, DataType> DEFAULT_COL_TYPES;

    static
    {
        DEFAULT_COL_TYPES = new HashMap<String, DataType>();
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[0], DataType.INT);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[1], DataType.STRING);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[2], DataType.STRING);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[3], DataType.STRING);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[4], DataType.LONG);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[5], DataType.DATE);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[6], DataType.BIN_STREAM);
        DEFAULT_COL_TYPES.put(DEFAULT_COL_NAMES[7], DataType.BIN_STREAM);
    }

	public PhotoDB(String hostname)
	{
        this(hostname, DEFAULT_COL_NAMES, DEFAULT_COL_TYPES, 0);
	}

    public PhotoDB(String hostname, String[] columnNames, HashMap<String, DataType> columnTypes, int primaryKey)
    {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.primaryKey = primaryKey;
		
        dbHostname = hostname;
        conn = null;
        
        // If the temp directory does not exist, create it
		File tempDir = new File(TEMP_PATH);
		if (!tempDir.exists())
			tempDir.mkdirs();
		
		log.info("PhotoDB constructed");
    }
	
    // Connect to the database with its current settings;
    // If PhotoDB is connected to a database, that connection
    // will be terminated
	public void connect()
	{
	    try {
	    	if (conn != null)
				conn.close();
	    	
	    	conn = DriverManager.getConnection(dbURLStart + dbHostname + "/" + dbName, user, password);
	    	log.info("Connected to database");
	    }
	    catch (SQLException e){
	    	e.printStackTrace();
	    	log.error("Error connecting to database");
	    }
	}
	
    // This method MUST be modified if one wants to use a custom table schema,
    // or data will not be inserted correctly (e.g. change how primary key is derived)
	public void loadFolder(String folderPath)
	{
		// Does NOT load recursively - only files in this folder
		File[] f = new File(folderPath).listFiles();
		String query = "SELECT MAX(INDEX) FROM Customers";
		
		for (int i = 0; i < f.length; i++)
        {
			if (f[i].isFile())
			{
                Object[] data = new Object[columnNames.length];
                
                // Preparing data
				String filename = f[i].getName();
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
				
				insertRow(data);
			}
        }
		log.info("END: FOLDER LOAD");
	}
	
	// INSERTS a row containing <code>data[]</code> into the database.
	//
	// The index of each object in data[] should correspond to the column in columnNames
	// and each object's type the type in columnTypes;
	// i.e. <code>data[i]</code> has data type <code>columnTypes.get(columnNames[i])</code>
	//     DataType of column:		Type of data[i]:
	// 		DataType.INT 		= 		Integer
	//		DataType.BOOLEAN 	= 		Boolean
	//		DataType.LONG 		= 		Long
	//		DataType.DOUBLE 	= 		Double
	//		DataType.STRING 	= 		String
	//		DataType.DATE 		= 		java.sql.Date
	//		DataType.TIME 		= 		java.sql.Time
	// 		DataType.BIN_STREAM	=		java.io.File
	public void insertRow(Object[] data)
	{
		String query = "INSERT INTO " + tableName + " VALUES (?";
        for (int i = 0; i < columnNames.length - 1; i++)
            query += ", ?";
        query += ")";

		PreparedStatement stmt = null;
		FileInputStream fis = null; 
		
		try {
            stmt = conn.prepareStatement(query);

 			for (int i = 0; i < columnNames.length; i++)
            {
 				// i + 1 for arg2, since setX starts at 1 (not 0 like arrays)
                setPrepStatementParam(stmt, i + 1, columnTypes.get(columnNames[i]), data[i]);
            }
            stmt.execute();
            
            log.info("Row loaded");
		}
		catch (Exception e) {
			e.printStackTrace();
	        log.error("ERROR inserting row");
	    }
	}
	
	// THE primary key must be properly set in order for this method to work
	//
	// Based on the <code>primaryKeyValue</code>, selects a row whose primary key
	// value matches that value, and returns the photo stored in that row.
	public Image getSpecificPhoto(Object primaryKeyValue)
	{
		Image image = null;
		PreparedStatement stmt = null;
		String query = "SELECT * FROM " + tableName + " WHERE `"			//Table name has to be hardcoded
				+ columnNames[primaryKey] + "`=?";							//can't insert column name as param, so it's here
		
		try {
			stmt = conn.prepareStatement(query);
			stmt.setObject(1, primaryKeyValue, columnTypes.get(columnNames[primaryKey]).getSqlType());
			ResultSet rs = stmt.executeQuery();
			
			rs.next();														//Reading the image
			for (int i = 0; i < columnNames.length; i++)					//Loop through columns to find image (desired) column
			{
				String colName = columnNames[i];
				DataType type = columnTypes.get(colName);
				if (type == DataType.BIN_STREAM && colName.indexOf("thumb") == -1)
				{
					InputStream in = rs.getBinaryStream(i + 1);				//ResultSets start at 1!
					image = ImageIO.read(in);
				}
			}
		} catch (Exception e ) {
	    	e.printStackTrace();
	        log.error("ERROR retrieving photo with primary key value: " + primaryKeyValue);
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
	
	//Retrieves photos from database and writes them to the temp directory
	public void retrievePhotos()
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
	
	//Retrieves photo properties from database
	public void retrievePhotoPropertiesOnly()
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

	//Can only be called if retrievePhotos() has been called >=1 time
	public File[] getRetrievedPhotos()
	{
		return currPhotos;
	}
	
	// Can only be called if either retrievePhotos() or retrievePhotoPropertiesOnly() has been called >=1 time
	public Properties[] getRetrievedPhotoProperties()						
	{
		return currProps;
	}
	
	// Deletes all files in the temp directory PhotoDB created
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
	
	// Returns a copy of the column names that has been set,
	// and which should match the current table schema
    public String[] getColumnNames()
    {
        String[] cols = new String[columnNames.length];
        for (int i = 0; i < cols.length; i++)
            cols[i] = columnNames[i];

        return cols;
    }
    
    // Sets the column which contains the unique identifier for
    // each row entry to primaryKey; the first column corresponds to 0
    public void setPrimaryKey(int primaryKey)
    {
        this.primaryKey = primaryKey;
    }

    public int getPrimaryKey()
    {
        return primaryKey;
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
	
	// Sets the (index)th parameter in the PreparedStatement to <code>data</code>
	// and ensures that it has the correct type since a <code>DataType</code> is also passed in
    private void setPrepStatementParam(PreparedStatement stmt, int index, DataType type, Object datum)
    {
        try {
        	// Allow null datum - just return immediately
        	if (datum == null)
        	{
        		stmt.setNull(index, type.getSqlType());
        		return;
        	}
            switch (type)
            {
                case INT:					//"Simple" types can all be set using setObject()
                case BOOLEAN:
                case LONG:
                case DOUBLE:
                case STRING:
                case DATE:
                case TIME:
                    stmt.setObject(index, datum, type.getSqlType());
                    break;
                case BIN_STREAM:
                    FileInputStream fis = null;
    
                    fis = new FileInputStream((File) datum);								//Inputting the blob
                    
                    // Photo-specific - if a column name contains the substring "thumb",
                    // assume that it intends to store thumbnails
                    if (columnNames[index - 1].toLowerCase().indexOf("thumb") > -1)
                    {
                        BufferedImage image = ImageIO.read(fis);
                        int w = image.getWidth() * 64 / image.getHeight(), h = 64;
                        BufferedImage buff = resizeImage(image, w, h);
                        
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        ImageIO.write(buff,"jpg", os); 
                        InputStream in = new ByteArrayInputStream(os.toByteArray());
                        stmt.setBinaryStream(index, in);
                    }
                    else
                        stmt.setBinaryStream(index, fis, ((File) datum).length());
                    break;
                default:
                    log.error("Data type not supported");
                    break;
            }
        } catch (Exception e) {
			e.printStackTrace();
	        log.error("ERROR setting PreparedStatement value");
	    }
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
