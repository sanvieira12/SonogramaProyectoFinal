package com.sonograma.dto;

import java.util.List;

public record VinylPageData(String frontImageUrl, String backImageUrl, List<TrackInfo> tracks) {}
