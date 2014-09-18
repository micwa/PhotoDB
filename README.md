PhotoDB - MySQL client/GUI for viewing and accessing photo databases.

## Overview

This project mainly consists of PhotoDB, which provides methods to
retrieve/modify rows and photos from a MySQL database, and the PhotoViewer GUI.

`PhotoDB` is the core of the project. It handles connecting to the database,
querying the database, and downloading files. The "helper" enum `DataType`
exists to allow clients to customize PhotoDB's table schema, i.e. tell PhotoDB
the structure of the table it is connecting to. See below for a full list of
features.

The GUI is designed to connect to the database; retrieve thumbnails which it
places in the sidebar to browse through; and get a specific photo from the
database when the user clicks on its corresponding thumbnail. Options are also
available for uploading and deleting photos in the database.

## Features

##### PhotoDB

Access-wise, PhotoDB has the ability to:

* Establish a connection to a MySQL database (note that this depends on
[MySQL Connector J] (http://dev.mysql.com/downloads/connector/j/), which, if
you are using the JAR, is included)
* Download all photos from the database and store them in a local directory
* Retrieve photos one at a time for viewing, and cache them locally for when
they are viewed again
* Retrieve only photo metadata (properties) and/or thumbnails for easier access
* Upload photos to the database based on the table row's unique identifier
* Delete photos from the database the same way

PhotoDB is also customizable. Clients and subclasses can:

* Specify a custom table schema, and not use `PhotoDB`'s defaults
* Set the unique key (column), the value of which is used to identify the photo
in the table being accessed
* Use the inner `StreamWriter` class to write files asynchronously and
start/stop writing them at will
* Modify and use `DataType` to allow `PhotoDB` to work with more data types
* Currently supported (SQL) data types are: `INTEGER`, `BOOLEAN`, `DOUBLE`,
`BIGINT`, `VARCHAR`, `DATE`, `TIME`, and `BLOB` (which is assumed to be a Java
binary stream)

See the documentation in the source files for more details on what PhotoDB can
and cannot do.

##### The GUI

PhotoViewer is the graphical user interface currently bundled with PhotoDB and
demonstrates how PhotoDB can be used; it is not meant to be a fully-fledged
application that implements every feature of PhotoDB. With that said,
PhotoViewer allows users to:

* Connect to and disconnect from different databases 
* Browse through all the photos from the connected database
* Automatically retrieve thumbnails of photos on connection and use `PhotoDB`'s
`getSpecificPhoto()` method to view and smartly cache files offline
* View properties for the current photo in a separate panel
* Upload photos and folders of photos to the database (non-recursive for
folders)
* Select and delete one or more photos from the database

### JAR File

An executable JAR file is included. The .jar was compiled with JDK 1.7
(javac version 1.7.0_67) and requires JRE 7 at minimum to run. 

If Java is not up to date or missing, download it at [Oracle's website]
(http://www.oracle.com/technetwork/java/javase/downloads/index.html).

### License

This software is released under the [GPLv3 license]
(http://www.gnu.org/copyleft/gpl.html).