package com.example.mptcalculator;

import javax.servlet.annotation.WebServlet;

import com.example.mptcalculator.Events.OpenInfoWindowOnMarkerClickListener;
import com.example.mptcalculator.Utils.Coordinate;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.FileResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.tapio.googlemaps.GoogleMap;
import com.vaadin.tapio.googlemaps.client.GoogleMapControl;
import com.vaadin.tapio.googlemaps.client.LatLon;
import com.vaadin.tapio.googlemaps.client.overlays.GoogleMapInfoWindow;
import com.vaadin.tapio.googlemaps.client.overlays.GoogleMapMarker;
import com.vaadin.ui.*;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This UI is the application entry point. A UI may either represent a browser window
 * (or tab) or some part of an HTML page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be
 * overridden to add component to the user interface and initialize non-component functionality.
 */
@Theme("mytheme")
public class MedianPickupTimeUI extends UI {
	
	@Override
	protected void init(VaadinRequest vaadinRequest) {
		getPage().setTitle("Wolt Median Pickup Time");
		
		final VerticalLayout layout = new VerticalLayout();
		layout.setSizeFull();
		
		final HorizontalLayout timeSelectionLayout = new HorizontalLayout();
		timeSelectionLayout.setCaption("Pickup times");
		
		final DateField dateField = new DateField("Date");
		dateField.setDateFormat("dd.MM.yyyy");
		dateField.setValue(LocalDate.now());
		
		final NativeSelect<String> startingTimeSelect = new NativeSelect<>("From");
		startingTimeSelect.setHeight(100, Unit.PERCENTAGE);
		startingTimeSelect.setEmptySelectionAllowed(false);
		
		final NativeSelect<String> endingTimeSelect = new NativeSelect<>("To");
		endingTimeSelect.setHeight(100, Unit.PERCENTAGE);
		endingTimeSelect.setEmptySelectionAllowed(false);
		
		List<String> fromHours = new ArrayList<>();
		List<String> toHours = new ArrayList<>();
		
		for (int i = 0; i < 23; i++) {
			fromHours.add(Integer.toString(i) + ":00");
			toHours.add(Integer.toString(i + 1) + ":00");
		}
		fromHours.add("23:00");
		toHours.add("00:00");
		
		startingTimeSelect.setItems(fromHours);
		startingTimeSelect.setSelectedItem(fromHours.iterator().next());
		endingTimeSelect.setItems(toHours);
		endingTimeSelect.setSelectedItem(toHours.iterator().next());
		
		timeSelectionLayout.addComponents(dateField, startingTimeSelect, endingTimeSelect);
		
		final HorizontalLayout buttonLayout = new HorizontalLayout();
		
		final Button calculateButton = new Button("Calculate MPT");
		final Button downloadButton = new Button("Download as CSV");
		
		buttonLayout.addComponents(calculateButton, downloadButton);
		
		AtomicReference<GoogleMap> mapAtomicReference = new AtomicReference<>();
		GoogleMap map = new GoogleMap("AIzaSyA7g0qaNZwBYqX1pgj3N_Z_NkPdrQEddZA", null, "english");
		map.setCenter(new LatLon(60.1676093, 24.940554));
		map.setZoom(13);
		map.setMinZoom(8);
		map.removeControl(GoogleMapControl.MapType);
		map.removeControl(GoogleMapControl.StreetView);
		map.removeControl(GoogleMapControl.Scale);
		map.setSizeFull();
		mapAtomicReference.set(map);
		
		layout.addComponents(timeSelectionLayout, buttonLayout, mapAtomicReference.get());
		layout.setExpandRatio(mapAtomicReference.get(), 1);
		
		setContent(layout);
		
		Map<Long, Long> medianTimesMap = new HashMap<>();
		final AtomicReference<String> csvFileName = new AtomicReference<>();
		
		calculateButton.addClickListener(event -> {
			// refresh Map
			layout.removeComponent(mapAtomicReference.get());
			GoogleMap newMap = new GoogleMap("AIzaSyA7g0qaNZwBYqX1pgj3N_Z_NkPdrQEddZA", null, "english");
			newMap.setCenter(new LatLon(60.1676093, 24.940554));
			newMap.setZoom(13);
			newMap.setMinZoom(8);
			newMap.removeControl(GoogleMapControl.MapType);
			newMap.removeControl(GoogleMapControl.StreetView);
			newMap.removeControl(GoogleMapControl.Scale);
			newMap.setSizeFull();
			mapAtomicReference.set(newMap);
			
			layout.addComponent(mapAtomicReference.get());
			layout.setExpandRatio(mapAtomicReference.get(), 1);
			medianTimesMap.clear();
			
			// read date time input
			Date date = Date.from(dateField.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant());
			int startingHour = Integer.parseInt(startingTimeSelect.getValue().split(":")[0]);
			int endingHour = Integer.parseInt(endingTimeSelect.getValue().split(":")[0]);
			
			csvFileName.set("median_pickup_times_" + date.getDate() + "." + date.getMonth() + "" + date.getYear() +
					"_" + Integer.toString(startingHour) + ":00_" + Integer.toString(endingHour) + ":00");
			
			if ((startingHour >= endingHour && endingHour != 0) || date == null) {
				Notification.show("Invalid time range", Notification.Type.WARNING_MESSAGE);
				return;
			}
			
			// setup csv parser
			CsvParserSettings settings = new CsvParserSettings();
			settings.setHeaderExtractionEnabled(true);
			CsvParser parser = new CsvParser(settings);
			
			// parse pickup_times.csv
			String basePath = "src" + File.separator + "main" + File.separator + "resources" + File.separator;
			File pickupTimesFile = new File(basePath + "pickup_times.csv");
			parser.parse(pickupTimesFile);
			
			parser.getRecordMetadata().setTypeOfColumns(Long.class, "location_id", "pickup_time");
			parser.getRecordMetadata().setTypeOfColumns(LocalDate.class, "iso_8601_timestamp");
			
			List<Record> pickupRecordsList = parser.parseAllRecords(pickupTimesFile);
			Map<Long, ArrayList<Long>> pickupTimesMap = new HashMap<>();
			
			// filter the data from pickup_times.csv
			for (Record record : pickupRecordsList) {
				Date recordDate = record.getDate("iso_8601_timestamp", "yyyy-MM-dd'T'HH:mm:ss'Z'");
				if (recordDate.getYear() != date.getYear() || recordDate.getMonth() != date.getMonth()
						|| recordDate.getDate() != date.getDate()) {
					continue;
				}
				
				if (recordDate.getHours() < startingHour || (recordDate.getHours() > endingHour && endingHour != 0)
						|| (recordDate.getHours() == endingHour && (recordDate.getMinutes() > 0 || recordDate.getSeconds() > 0))) {
					continue;
				}
				
				long id = record.getLong("location_id");
				long time = record.getLong("pickup_time");
				
				if (pickupTimesMap.containsKey(id)) {
					pickupTimesMap.get(id).add(time);
				} else {
					ArrayList<Long> list = new ArrayList<>();
					list.add(time);
					pickupTimesMap.put(id, list);
				}
			}
			
			// parse locations.csv
			File locationsFile = new File(basePath + "locations.csv");
			parser.parse(locationsFile);
			
			parser.getRecordMetadata().setTypeOfColumns(Long.class, "location_id");
			parser.getRecordMetadata().setTypeOfColumns(Double.class, "longitude", "latitude");
			List<Record> locationsRecordList = parser.parseAllRecords(locationsFile);
			Map<Long, Coordinate> locationsMap = new HashMap<>();
			
			for (Record record : locationsRecordList) {
				Long id = record.getLong("location_id");
				Double lon = record.getDouble("longitude");
				Double lat = record.getDouble("latitude");
				
				locationsMap.put(id, new Coordinate(lon, lat));
			}
			
			// calculate median time
			for (Map.Entry<Long, ArrayList<Long>> entry : pickupTimesMap.entrySet()) {
				Collections.sort(entry.getValue());
				int listSize = entry.getValue().size();
				long median;
				if (listSize % 2 == 0) {
					median = (entry.getValue().get(listSize / 2) + entry.getValue().get(listSize / 2 - 1)) / 2;
				} else {
					median = entry.getValue().get(listSize / 2);
				}
				
				medianTimesMap.put(entry.getKey(), median);
				
				// add marker to google maps
				Coordinate latLon = locationsMap.get(entry.getKey());
				
				GoogleMapMarker marker = new GoogleMapMarker();
				marker.setAnimationEnabled(false);
				marker.setCaption(null);
				marker.setDraggable(false);
				marker.setIconUrl("VAADIN/markers/marker_blue" + Long.toString(median) + ".png");
				marker.setId(entry.getKey());
				marker.setOptimized(true);
				marker.setPosition(new LatLon(latLon.getLat(), latLon.getLon()));
				mapAtomicReference.get().addMarker(marker);
				
				// add info window to marker
				GoogleMapInfoWindow infoWindow = new GoogleMapInfoWindow("Restaurnant"
						+ Long.toString(entry.getKey()), marker);
				infoWindow.setWidth("500px");
				infoWindow.setHeight("300px");
				
				VerticalLayout infoWindowLayout = new VerticalLayout();
				infoWindowLayout.setSizeFull();
				
				Label restaurantNameLabel = new Label("Restaurant name: " + Long.toString(entry.getKey()));
				restaurantNameLabel.setWidthUndefined();
				
				FileResource resource = new FileResource(new File("src/main/webapp/VAADIN/default.jpg"));
				Image restaurantImage = new Image(null, resource);
				restaurantImage.setSizeFull();
				
				infoWindowLayout.addComponents(restaurantNameLabel, restaurantImage);
				
				mapAtomicReference.get().setInfoWindowContents(infoWindow, infoWindowLayout);
				
				// add marker clicked listener
				OpenInfoWindowOnMarkerClickListener infoWindowOpener =
						new OpenInfoWindowOnMarkerClickListener(mapAtomicReference.get(), marker, infoWindow);
				mapAtomicReference.get().addMarkerClickListener(infoWindowOpener);
			}
		});
		
		downloadButton.addClickListener(event -> {
			if (medianTimesMap == null || medianTimesMap.size() < 1) {
				Notification.show("Calculate first", Notification.Type.WARNING_MESSAGE);
			}
		});
	}
	
	@WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
	@VaadinServletConfiguration(ui = MedianPickupTimeUI.class, productionMode = false)
	public static class MyUIServlet extends VaadinServlet {
	}
}
