A generically named PhotoDB application that is intended to be a frontend/GUI
for viewing and accessing a MySQL photo database.

## Overview

This project mainly consists of PhotoDB, which provides methods to retrieve
and modify photos from a MySQL database (or to be more specific, one table per
connection), and PhotoViewer, with its GUI and subcomponents.

## JAR File

An executable JAR file is included. The .jar was compiled with JDK 1.7
(javac version 1.7.0_67) and requires JRE 7 at minimum to run. 

If Java is not up to date or missing, one can download it at [Oracle's website] (http://www.oracle.com/technetwork/java/javase/downloads/index.html).

## Bugs

Some current bugs/things to work on:

* Fix loadFolder()
* Settings are not saved if the windowClosing() event is not triggered when
the dialog is confirmed or canceled
* The textboxes in the settings dialog are not tall enough (thus sometimes
obscuring the entered text)