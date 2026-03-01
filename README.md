Schnell-Imbiss
Overview

Schnell-Imbiss is a university project developed in Java to simulate the management of orders in a fast-food / sandwich shop environment.
The application focuses on order creation, order handling, data organization, and basic persistence through a local database.

The project was designed for educational purposes, with the goal of applying software engineering principles such as:

object-oriented programming

modular design

data persistence

testing

separation between logic and data structures

Project Goals

The main goals of this project are:

model the workflow of a small food ordering system

manage customers, orders, and ordered items

organize the code using a clear and maintainable structure

interact with a database for storing and retrieving data

provide a simple demonstration of the application behavior

Technologies Used

Java

Maven

H2 Database

JUnit for testing

Eclipse / VS Code as development environment

Project Structure

The project is organized into multiple packages, each with a specific responsibility:

control
Contains the main business logic and controller classes.

dto
Contains Data Transfer Objects used to move structured data between different parts of the application.

model
Contains domain classes and enumerations representing the core entities of the system.

gui
Contains the main entry point or simple interface-related classes.

resources
Contains SQL scripts and database-related resources.

test
Contains unit tests for validating the system behavior.

Main Components

Some relevant classes included in the project are:

GestioneOrdiniController
Manages the order workflow and the main application logic.

DbInit
Initializes the database structure and prepares the environment.

OrderDemo
Provides an example execution flow of the system.

Main
Application entry point.

DTO classes
Represent customers, order items, sandwiches, riders, and related data in a structured way.
