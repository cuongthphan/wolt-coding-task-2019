package com.example.mptcalculator.Utils;

public class Coordinate {
	double lon;
	double lat;
	
	public Coordinate(double lon, double lat) {
		this.lon = lon;
		this.lat = lat;
	}
	
	public Coordinate(Double lon, Double lat) {
		this.lon = lon;
		this.lat = lat;
	}
	
	public double getLon() {
		return lon;
	}
	
	public double getLat() {
		return lat;
	}
}
