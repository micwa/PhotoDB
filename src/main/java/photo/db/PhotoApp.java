package photo.db;

import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.apache.log4j.Logger;

public class PhotoApp
{
	private static final Logger log = Logger.getLogger(PhotoApp.class.getName());
	
	public static void main(String[] args)
	{
		PhotoDB db = new PhotoDB("localhost:3306");
		PhotoViewer viewer = new PhotoViewer(db);
		
		viewer.setBounds(200, 200, 800, 600);
		viewer.setBackground(Color.WHITE);
		viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    //w.setResizable(false);
		viewer.setVisible(true);
	    
		viewer.addWindowListener(new WindowCloseListener(db));
	}
	
	private static class WindowCloseListener extends WindowAdapter
	{		
		private PhotoDB db;
		
		public WindowCloseListener(PhotoDB db)
		{
			this.db = db;
		}
		
		public void windowClosing(WindowEvent e)
		{
			db.deleteTempFiles();
			log.info("Window closed");
		}
	}
}
