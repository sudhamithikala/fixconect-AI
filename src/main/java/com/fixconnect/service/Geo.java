package com.fixconnect.service;

/** Haversine distance helper. */
public final class Geo {

    private Geo() {
    }

    public static Double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(km * 10.0) / 10.0;
    }

    /** Rough ETA assuming 25 km/h city traffic plus 5 minutes to set off. */
    public static Integer etaMinutes(Double distanceKm) {
        if (distanceKm == null) {
            return null;
        }
        return (int) Math.ceil(distanceKm / 25.0 * 60) + 5;
    }
}
