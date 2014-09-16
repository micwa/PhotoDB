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
