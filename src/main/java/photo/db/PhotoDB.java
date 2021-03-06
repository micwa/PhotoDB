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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

public class PhotoDB
{
    // Variables required to connect to database
    private String dbURLStart = "jdbc:mysql://";
    private String dbHostname;
    private String dbName;
    private String tableName;
    private String user, password;
    protected Connection conn;

    // Array that stores column order, Hashmap that stores column names & type
    private String[] columnNames;
    private HashMap<String, DataType> columnTypes;
    
    // Retrieved photos & properties
    private File[] currPhotos;
    private Properties[] currProps;
    // Cached photos from getSpecificPhoto(), and whether the cache has completed or not
    private ArrayList<File> cachedPhotos;
    private HashMap<File, Boolean> cachedDone;
    private ArrayList<StreamWriter> streamWriters;
    // Path where ALL retrieved/cached photos are stored
    private String photoPath = "photodb_local";
    
    // The column of the unique key, to identify each row entry
    private int uniqueKey;
    
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

    /**
     * Initializes a new instance of this class and creates the temp directory
     * if it does not already exist.
     * 
     * @param hostname The hostname of the database
     * @param columnNames An array of Strings that exactly the match the column names
     * of the table intended to be accessed; the column names should also be in the
     * same order as the Strings in the array.
     * There are no restrictions on names, except that any column containing the substring
     * "thumb" in any (upper/lower) case will be construed as being the thumbnail column.
     * In the rare case that two (or more) columns contain "thumb", the first column
     * is assumed to be the thumbnail column.
     * 
     * @param columnTypes A HashMap<String, DataType> with
     * <code>columnNames.length</code> number of keys that correspond to the Strings in
     * columnNames, and respective DataTypes that correspond to the proper data type.
     * Restrictions: A maximum of two BIN_STREAM types can be set per row (i.e. the
     * BIN_STREAM representing the image and that representing the thumbnail).
     * Otherwise, only the first BIN_STREAM that is not in the thumbnail column will
     * be considered and later BIN_STREAMs in the same row will be ignored.
     * (this applies in retrievePhotos(), getSpecificPhoto(), getPhotoThumbnails())
     * 
     * @param uniqueKey The column of the key used to identify each row
     * in the table, where the first row is 0, the second row is 1, etc.
     * It must be properly set in order for getSpecificPhoto() to work, but it is
     * not necessary for retrievePhotos().
     */
    public PhotoDB(String hostname, String[] columnNames, HashMap<String, DataType> columnTypes, int uniqueKey)
    {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.uniqueKey = uniqueKey;
        
        dbHostname = hostname;
        conn = null;
        
        // If the temp directory does not exist, create it
        File tempDir = new File(photoPath);
        if (!tempDir.exists())
            tempDir.mkdirs();
    }

    /**
     * Connect to the database with its current settings; if PhotoDB is
     * connected to a database, that connection will be terminated. All methods
     * that access the database (insertRow(), retrieve*(), getSpecPhoto(),
     * getPhotoThumbs(), getAllUniqueKeys()) will throw IllegalStateExceptions
     * if this method is not called first.
     *
     * The photo cache for getSpecificPhoto() is also reset per connection
     * (although if deletePhotoDirectory() is not called, those files will
     * remain and be available for use by getSpecificPhoto()).
     * 
     * @throws SQLException If there is an error connecting to the database
     */
    public void connect() throws SQLException
    {
        if (conn != null)
            conn.close();

        cachedPhotos = new ArrayList<File>();
        cachedDone = new HashMap<File, Boolean>();
        streamWriters = new ArrayList<StreamWriter>();
        conn = DriverManager.getConnection(dbURLStart + dbHostname + "/" + dbName, user, password);
    }
    
    /**
     *  Manually disconnect from database and sets the current connection
     *  to null.
     *  
     *  @throws SQLException If there is an error disconnecting from the database
     */
    public void disconnect() throws SQLException
    {
        if (conn != null)
            conn.close();
        conn = null;
    }
    
    /**
     * Inserts a row containing <code>data[]</code> into the database as long
     * as the row's unique key does not already exist in the database. Returns
     * true if the insertion was successful, and false if not.
     * 
     * @param data The array of data to be inserted.
     * The index of each object in data[] should correspond to the column in columnNames
     * and each object's type the type in columnTypes;
     * i.e. <code>data[i]</code> has data type <code>columnTypes.get(columnNames[i])</code>
     *    DataType of column:       Type of data[i] expected:
     *      DataType.INT        =       Integer
     *      DataType.BOOLEAN    =       Boolean
     *      DataType.LONG       =       Long
     *      DataType.DOUBLE     =       Double
     *      DataType.STRING     =       String
     *      DataType.DATE       =       java.sql.Date
     *      DataType.TIME       =       java.sql.Time
     *      DataType.BIN_STREAM =       java.io.File
     * 
     * @return <code>true</code> if <code>data[]</code> is inserted properly, <code>false</code>
     * if the unique key value in <code>data[]</code>already exists in the database
     * @throws SQLException If there is an error executing the query
     */
    public boolean insertRow(Object[] data) throws SQLException
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
        
        // Check if the unique key value in <code>data</code> already exists
        // somewhere in the database
        String check = "SELECT * FROM " + tableName + " WHERE `" + columnNames[uniqueKey] + "`=?";
        PreparedStatement stmt = null, stmtCheck = null;
        
        try {
            stmtCheck = conn.prepareStatement(check);
            stmtCheck.setObject(1, data[uniqueKey], columnTypes.get(columnNames[uniqueKey]).getSqlType());
            ResultSet rs = stmtCheck.executeQuery();

            // If the ResultSet contains a row, then don't insert anything
            if (rs.next())
                return false;

            // Now insert the row since checking is done
            String query = "INSERT INTO " + tableName + " VALUES (?";
            for (int i = 0; i < columnNames.length - 1; i++)
                query += ", ?";
            query += ")";

            stmt = conn.prepareStatement(query);

            for (int i = 0; i < columnNames.length; i++)
            {
                // i + 1 for arg2, since setX starts at 1 (not 0 like arrays)
                setPrepStatementParam(stmt, i + 1, columnTypes.get(columnNames[i]), data[i]);
            }
            stmt.execute();    
        } catch (SQLException ex) { throw ex; }
        finally {
            if (stmt != null) stmt.close();
            if (stmtCheck != null) stmt.close();
        }

        return true;
    }
    
    /**
     * (The unique key must be properly set in order for this method to work.)
     * This method attempts to deletes the row that corresponds to the passed-in
     * <code>uniqueKeyValue</code>. Returns true if the row is successfully deleted,
     * and false if not.
     * 
     * @param uniqueKeyValue The value of the unique key that corresponds to the
     * row to be deleted
     * @return <code>true</code> if the deletion is successful, false if not
     * @throws SQLException If there is an error executing the query
     */
    public boolean deleteRow(Object uniqueKeyValue) throws SQLException
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
        
        PreparedStatement stmt = null;
        String query = "DELETE FROM " + tableName + " WHERE `" + columnNames[uniqueKey]    + "`=?";
        
        try {
            // Begin deletion
            stmt = conn.prepareStatement(query);
            stmt.setObject(1, uniqueKeyValue, columnTypes.get(columnNames[uniqueKey]).getSqlType());
            
            if (stmt.executeUpdate() == 1)                                    //Since only attempt to delete one row
                return true;
            else
                return false;
        } catch (SQLException ex) { throw ex; }
        finally {
            if (stmt != null) stmt.close();
        }
    }

    /**
     * Retrieves photos from database and writes them to the photo directory
     * in the order that they were inserted into the database.
     * This method retrieves and stores the photo properties as well.
     * 
     * @throws SQLException If there is an error executing the query
     */
    public void retrievePhotos() throws SQLException
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
                
        PreparedStatement stmt = null;
        String query = "SELECT * FROM " + tableName;
        ArrayList<File> paths = new ArrayList<File>();
        ArrayList<Properties> props = new ArrayList<Properties>();
        
        try {
            stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();                        //Getting rows from table

            while (rs.next())
            {
                Properties tempProp = new Properties();

                for (int i = 0; i < columnNames.length; i++)
                {
                    Object obj = getResultSetParam(rs, i + 1, columnTypes.get(columnNames[i]));

                    // If it's the image, add path and skip setting properties
                    if (obj instanceof File)
                    {
                        paths.add((File) obj);                                        //Add even if file exists already
                        continue;
                    }
                    if (obj != null)
                        tempProp.setProperty(columnNames[i], obj.toString());
                }
                props.add(tempProp);
            }

            // Store photo file paths - return Image[] in getRetrievedPhotos()
            currPhotos = paths.toArray(new File[paths.size()]);
            currProps = props.toArray(new Properties[props.size()]);
        } catch (SQLException ex) { throw ex; }
        finally {
            if (stmt != null) stmt.close();
        }
    }
    
    /**
     * Basically retrievePhotos(), except it skips writing the images to disk
     * 
     * @throws SQLException If there is an error executing the query
     */
    public void retrievePhotoPropertiesOnly() throws SQLException
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
                
        PreparedStatement stmt = null;
        String query = "SELECT * FROM " + tableName;
        ArrayList<Properties> props = new ArrayList<Properties>();
        
        try {
            stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();                        //Getting rows from table

            while (rs.next()) 
            {
                Properties tempProp = new Properties();

                for (int i = 0; i < columnNames.length; i++)
                {
                    DataType type = columnTypes.get(columnNames[i]);

                    //Skip BIN_STREAMs since they aren't properties
                    if (type == DataType.BIN_STREAM)
                        continue;

                    Object obj = getResultSetParam(rs, i + 1, columnTypes.get(columnNames[i]));
                    if (obj != null)
                        tempProp.setProperty(columnNames[i], obj.toString());
                }
                props.add(tempProp);
            }

            currProps = props.toArray(new Properties[props.size()]);
        } catch (SQLException ex) { throw ex; }
        finally {
            if (stmt != null) stmt.close();
        }
    }

    /**
     * Can only be called if retrievePhotos() has been called >=1 time
     * 
     * @return Image[] array, which contains photos that have been previously
     * written to disk, or null if the retrieve photos can not be read (error)
     */
    public Image[] getRetrievedPhotos()
    {
        Image[] imgs = new Image[currPhotos.length];
        
        try {
            for (int i = 0; i < imgs.length; i++)
                imgs[i] = ImageIO.read(currPhotos[i]);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        
        return imgs;
    }
    
    /**
     * (The unique key must be properly set in order for this method to work.)
     * Based on the <code>uniqueKeyValue</code>, selects a row whose unique key value
     * matches that value, and caches and returns the photo stored in that row.
     *
     * This ALSO caches the photo in the photo directory similarly to retrievePhotos().
     * However, unlike retrievePhotos(), the file-writing is asynchronous so the image
     * can be viewed before the stream writer finishes writing.
     *
     * Notes on the caching: this method will first determine if retrievePhotos() has been
     * called. If it has, it will use those photos and not cache anything. If not,
     * it will determine whether the requested photo has already been cached during this connection,
     * and use that cache if it has. Otherwise, it will attempt to cache the file
     * to disk. (If a file with the same name already exists in the temp directory, it
     * will return an Image object of that file.)
     * 
     * @param uniqueKeyValue The value of the unique key for the photo that is
     * intended to be retrieved.
     * @return An <code>Image</code> that corresponds to the uniqueKeyValue, or null
     * if there is no such image or an exception is thrown
     */
    public Image getSpecificPhoto(Object uniqueKeyValue)
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
                
        Image image = null;
        PreparedStatement stmt = null;
        String query = "SELECT * FROM " + tableName + " WHERE `"                        //Table name has to be hardcoded
                + columnNames[uniqueKey] + "`=?";                                        //Can't insert column name as param, so it's here
        
        try {
            stmt = conn.prepareStatement(query);
            stmt.setObject(1, uniqueKeyValue, columnTypes.get(columnNames[uniqueKey]).getSqlType());
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next())
                return null;
            
            // Get the filename first
            String filename = rs.getObject(uniqueKey + 1).toString();
            File file = new File(photoPath + "\\" + filename);

            // If for some reason retrievePhotos() was called, use currPhotos
            // -- Note: For this to work, the filename for the temp file should be
            //        the same as in getPrepStatementParam()
            if (currPhotos != null && currPhotos.length > 0)
                for (File f : currPhotos)
                    if (file.equals(f))
                        return ImageIO.read(file);
            
            // Now check if the file had been cached by THIS method and the thread writing
            // it is no longer alive
            if (cachedPhotos.contains(file) && cachedDone.get(file))
                return ImageIO.read(file);
            // If file's not in either of those arrays, then attempt to cache it
            else
            {
                InputStream in = null;
                int index = 1;                                                //Init at 1
                
                for (int i = 0; i < columnNames.length; i++)
                {
                    String colName = columnNames[i];
                    DataType type = columnTypes.get(colName);
                    if (type == DataType.BIN_STREAM && colName.toLowerCase().indexOf("thumb") == -1)
                    {
                        index += i;
                        break;
                    }
                }
                // Start writing the image using a separate InputStream IFF a thread doesn't already exist to write it
                // If the file already exists, the thread will terminate immediately
                if (!cachedPhotos.contains(file))
                {
                    StreamWriter sw = new StreamWriter(rs.getBinaryStream(index), file);
                    Thread t = new Thread(sw);
                    t.start();
                    cachedDone.put(file, false);
                    streamWriters.add(sw);
                    cachedPhotos.add(file);                                    //Add if already existed on disk or not
                }
                
                // Read the image
                in = rs.getBinaryStream(index);
                image = ImageIO.read(in);
            }
        } catch (Exception e ) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                stmt.close();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        return image;
    }
    
    /**
     * ASSUMING that the database contains thumbnail images AND a table column
     * contains the substring "thumb" in upper or lowercase (or a mix), this method
     * returns an array of those thumbnail images in the order that they were inserted.
     *
     * Use this in conjunction with getSpecificPhoto() and retrievePhotoPropertiesOnly(),
     * or use retrievePhotos() by itself.
     * 
     * @return The array of thumbnail images in the database, or null if the database
     * query fails to execute
     */
    public Image[] getPhotoThumbnails()
    {
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
                
        ArrayList<Image> thumbs = new ArrayList<Image>();
        PreparedStatement stmt = null;
        
        // Same as in getSpecificPhoto(), except you want the thumbnail
        // and ALL rows are selected through the query
        String thumbCol = "";
        for (String colName : columnNames)
        {
            DataType type = columnTypes.get(colName);
            if (type == DataType.BIN_STREAM && colName.toLowerCase().indexOf("thumb") > -1)
            {
                thumbCol = colName;
                break;
            }
        }
        String query = "SELECT `" + thumbCol + "` FROM " + tableName;
        
        try {
            stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
    
            while (rs.next())
            {
                InputStream in = rs.getBinaryStream(1);
                thumbs.add(ImageIO.read(in));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                stmt.close();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        return thumbs.toArray(new Image[thumbs.size()]);
    }
    
    /**
     * Deletes all files that have been retrieved or cached by PhotoDB in the
     * photo path directory that was set before, and deletes the directory as
     * well if there are no other files present. Also, all StreamWriters are
     * stopped so files can be unlocked for deletion.
     * 
     * Note that if the photo directory is changed after caching/retrieval,
     * the files cached and retrieved prior to the change will not be deleted.
     */
    public void deletePhotoDirectory()
    {
        File tempDir = new File(photoPath);
        if (tempDir.exists())
        {
            if (streamWriters != null)
                for (StreamWriter sw : streamWriters)
                    sw.kill();                                                //Works because it's in a different thread :)
            if (currPhotos != null)
                for (File f : currPhotos)
                    f.delete();    
            if (cachedPhotos != null)
                for (File f : cachedPhotos)
                    f.delete();

            tempDir.delete();
        }
    }

// ----- GETTERS AND SETTERS for fields ----- //

    /**
     * Returns a COPY of the column names that have been set,
     * and which should match the current table schema.
     * 
     * @return a copy of the column names used by this PhotoDB
     */
    public String[] getColumnNames()
    {
        String[] cols = new String[columnNames.length];
        for (int i = 0; i < cols.length; i++)
            cols[i] = columnNames[i];
        
        return columnNames;
    }
    
    /**
     * Returns a COPY of the mappings of column names and data types
     * 
     * @return Hash<String, DataType> of the column names and data types
     */
    public HashMap<String, DataType> getColumnTypes()
    {
        HashMap<String, DataType> hash = new HashMap<String, DataType>();
        for (Map.Entry<String, DataType> entry : hash.entrySet())
            hash.put(entry.getKey(), entry.getValue());
        
        return columnTypes;
    }
    
    public String getPhotoDirectory()
    {
        return photoPath;
    }
    
    /**
     * Returns the (canonical) PATHS to the images, not the images themselves
     * 
     * @return A <code>File</code> array that represents all retrieved photos
     */
    public File[] getRetrievedPhotoPaths()
    {
        return currPhotos;
    }
    
    /**
     * Can only be called if either retrievePhotos() or retrievePhotoPropertiesOnly() has been called >=1 time
     * 
     * @return A <code>Properties</code> array that represents the properties
     * of all retrieved photos
     */
    public Properties[] getRetrievedPhotoProperties()                        
    {
        return currProps;
    }
    
    public int getUniqueKey()
    {
        return uniqueKey;
    }
    
    /**
     * Returns an array of ALL unique keys for each row entry in the table,
     * in the order in which they were inserted. Thus, the nth unique key will match
     * the nth photo and thumbnail from getRetrievedPhotos() and getPhotoThumbnails().
     * 
     * Note that if the database is updated after this method is called, the array
     * will no longer be consistent with the entries in the database and this method
     * will have to be called again.
     * 
     * @return An <code>Object</code> array of all the unique keys stored in the
     * database, or null if the database query fails to execute
     */
    public Object[] getAllUniqueKeys()
    {
        // If there's no connection, return immediately
        if (conn == null)
            throw new IllegalStateException("Not connected to any database");
        
        ArrayList<Object> objs = new ArrayList<Object>();
        PreparedStatement stmt = null;
        String query = "SELECT `" + columnNames[uniqueKey] + "` FROM " + tableName;

        try {
            stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            while (rs.next())
                objs.add(rs.getObject(1));
            
        } catch (SQLException e) {
            e.printStackTrace(); 
            return null;
        }

        return objs.toArray();
    }
    
    /**
     * Sets the directory which PhotoDB uses for caching/retrieving purposes,
     * specifically retrievePhotos() and getSpecificPhoto().
     * 
     * @param filepath The filepath to the directory to store cached photos in
     */
    public void setPhotoDirectory(String filepath)
    {
        photoPath = filepath;
    }
    
    public void setColumnNames(String[] columnNames)
    {
        this.columnNames = columnNames;
    }
    
    public void setColumnTypes(HashMap<String, DataType> columnTypes)
    {
        this.columnTypes = columnTypes;
    }
    
    /**
     * Sets the column which contains the unique key/identifier for
     * each row to uniqueKey, where the first column corresponds to a unique
     * key value of 0.
     * 
     * @param uniqueKey The value of the unique key to be set
     */
    public void setUniqueKey(int uniqueKey)
    {
        this.uniqueKey = uniqueKey;
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
    
    /**
     * Sets the (index)th parameter in the PreparedStatement to <code>data</code>
     * and ensures that it has the correct type since a <code>DataType</code> is also passed in.
     * 
     * @param stmt The <code>PreparedStatement</code> to use
     * @param index The column index of the <code>PreparedStatement</code> to insert into
     * @param type The data type of the object to be inserted
     * @param datum The object to be set at column index <code>index</code>
     * @throws SQLException If object <code>datum</code> fails to be set
     */
    protected void setPrepStatementParam(PreparedStatement stmt, int index, DataType type, Object datum) throws SQLException
    {
        // Datum may be null - just return immediately
        if (datum == null)
        {
            stmt.setNull(index, type.getSqlType());
            return;
        }
        switch (type)
        {
        case INT:                    //"Simple" types can all be set using setObject()
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

            try {
                fis = new FileInputStream((File) datum);
                
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
            } catch (IOException e) { e.printStackTrace(); }
            finally {
                try {
                    if (fis != null) fis.close();
                } catch (IOException e) { e.printStackTrace(); }
            }
            break;
        default:
            throw new IllegalArgumentException("Data type not supported");
        }
    }
    
    /**
     * For all data types except DataType.BIN_STREAM, this method returns the Java object corresponding
     * to the SQL type; for DataType.BIN_STREAM, this method writes the file to the temp directory
     * (UNLESS the column name for that index contains "thumb", i.e. stores a thumbnail, in
     * which case this method does nothing) and returns a java.io.File that refers to the
     * written file. The thread that writes the file is NOT asynchronous.
     *
     * Note: The type of the returned object *should* be the same as the type that was
     * inserted at the (index)th column. Still, cast at your discretion :)
     * 
     * @param rs The <code>ResultSet</code> to get the object from
     * @param index The column index of the <code>ResultSet</code> to obtain an object from
     * @param type The data type of the obtained object - only used to distinguish between
     * DataType.BIN_STREAM and other objects
     * @return
     * @throws SQLException If there is a problem getting an object from the <code>ResultSet</code>
     */
    protected Object getResultSetParam(ResultSet rs, int index, DataType type) throws SQLException
    {
        switch (type)
        {
            case INT:            
            case BOOLEAN:
            case LONG:
            case DOUBLE:
            case STRING:
            case DATE:
            case TIME:
                return rs.getObject(index);
            case BIN_STREAM:
                // If it is the thumbnail column, don't write anything to disk return null
                if (columnNames[index - 1].toLowerCase().indexOf("thumb") > -1)
                    return null;
                
                // Get the file and InputStream ready for the StreamWriter
                String filename = rs.getObject(uniqueKey + 1).toString();
                InputStream in = rs.getBinaryStream(index);
                File file = new File(photoPath + "\\" + filename);
                
                Thread t = new Thread(new StreamWriter(in, file));
                t.start();
                try {
                    t.join();                                                //Wait for this thread - not async
                } catch (InterruptedException e) { e.printStackTrace(); }
                
                return file;
            default:
                throw new IllegalArgumentException("Data type not supported");
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
    
    /**
     * A minimalist file-writer implementing <code>Runnable</code> to be used
     * for asynchronous and/or synchronized file writes. This class provides the
     * ability to start, stop and resume, and completely, kill file-writes.
     */
    protected class StreamWriter implements Runnable
    {
        private InputStream in;
        private File file;
        private boolean doRun, isAlive;
        
        public StreamWriter(InputStream in, File file)
        {
            this.in = in;
            this.file = file;
            doRun = true;
            isAlive = true;
        }
        
        /**
         * If the file does not exist, writes the file to disk and calls <code>kill()</code>
         * on itself after completion. Otherwise, this method does nothing.
         */
        public void run()
        {
            if (!file.exists())
            {
                OutputStream os = null;
                try {
                    os = new FileOutputStream(file);                //Writing the stream to disk
                    int c = 0;
                    
                    // While this StreamWriter is still "alive", write if this hasn't
                    // been stopped; if it has, Thread.sleep until it is resumed
                    while (isAlive)
                    {
                        while (isAlive && doRun && (c = in.read()) != -1)    //Constantly check for kill()/stop() signal
                            os.write(c);
                        
                        if (!doRun)
                            Thread.sleep(100);
                        else                                        //Entering here means the StreamWriter has either been
                        {                                           //killed OR the file has finished writing, so set done
                            this.kill();
                            cachedDone.put(file, true);    
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
                finally {
                    try {
                        if (in != null) in.close();
                        if (os != null) os.close();
                    } catch (IOException e) { e.printStackTrace(); }
                }
            }
        }
        
        /**
         * If this <code>StreamWriter</code> has been stopped, resume writing
         * the file if it has not yet been written. Otherwise, this method
         * does nothing.
         */
        public void resume()
        {
            doRun = true;
        }
        
        /**
         * Tells this <code>StreamWriter</code> to stop writing to the file.
         * File-writing can be resumed with a call to <code>resume()</code>.
         * 
         * Hint: This has to be called from a different thread than the one run() is
         * running on.
         */
        public void stop()
        {
            doRun = false;
        }        
        
        /**
         * This method prevents the <code>StreamWriter</code> from writing any
         * further and causes it to release all resources it is using. Note that run() calls
         * this method after it finishes writing the file, so the client does not
         * need to do so in normal circumstances.
         */
        public void kill()
        {
            isAlive = false;
        }
    }
}
