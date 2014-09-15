package photo.db;

import java.util.HashMap;

import photo.db.PhotoDB;

public class PhotoDBTest
{
	public static void main(String[] args)
	{
		//test_table();
		
		PhotoDB db = new PhotoDB("localhost:3306");
		db.setDBName("media_db");
		db.setTableName("photo_table");
		db.setUser("root");
		db.setPassword("admin");
		db.connect();
	}
	
	public static void test_table()
	{
		String[] cols = { "index", "filename", "doub", "date" };
		HashMap<String, DataType> hash = new HashMap<String, DataType>();
		hash.put(cols[0], DataType.INT);
		hash.put(cols[1], DataType.STRING);
		hash.put(cols[2], DataType.DOUBLE);
		hash.put(cols[3], DataType.DATE);
		
		PhotoDB db = new PhotoDB("localhost:3306", cols, hash, 0);
		db.setDBName("media_db");
		db.setTableName("test_table");
		db.setUser("root");
		db.setPassword("admin");
		db.connect();
		
		// Data
		Object[] data = new Object[cols.length];
		data[0] = 5;
		data[1] = null;
		data[2] = 3.56;
		data[3] = new java.sql.Date(new java.util.Date().getTime());
		db.insertRow(data);
	}
}
