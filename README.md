A generically named PhotoDB application that is intended to be a frontend/GUI
for viewing and accessing a MySQL photo database.

## Overview

This project mainly consists of PhotoDB, which provides methods to retrieve
and modify photos from a MySQL database (or to be more specific, one table per
connection), and the PhotoViewer GUI.

More documentation to come later.

## JAR File

An executable JAR file is included. The .jar was compiled with JDK 1.7
(javac version 1.7.0_67) and requires JRE 7 at minimum to run. 

If Java is not up to date or missing, one can download it at [Oracle's website] (http://www.oracle.com/technetwork/java/javase/downloads/index.html).

## Bugs/TODO

Some current bugs/things to work on:

* Fix thumbnail sizes
* Re-write retrievePhoto/Properties methods in PhotoDB
* Re-write updatePhotoProperties() in PhotoPanel
* The textboxes in the settings dialog are not tall enough (thus sometimes
obscuring the entered text)