package photo.db;

import photo.db.PhotoDB;

public class PhotoDBTest
{

	public static void main(String[] args)
	{
		PhotoDB db = new PhotoDB("localhost:3306");
		db.connect();
	}

}
