import type { StyleSpecification } from "maplibre-gl"

export const mapConfig = {
    longitude: 116.397428,
    latitude: 39.90923,
    zoom: 12,
    areaOfInterest: {
        type: "FeatureCollection" as const,
        features: [{
            type: "Feature" as const,
            properties: {},
            geometry: {
                type: "Polygon" as const,
                coordinates: [[
                    [116.35, 39.88],
                    [116.45, 39.88],
                    [116.45, 39.94],
                    [116.35, 39.94],
                    [116.35, 39.88],
                ]],
            },
        }],
    },
}

export const amapStyle: StyleSpecification = {
    version: 8,
    sources: {
        amap: {
            type: "raster",
            tiles: [
                "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
            ],
            tileSize: 256,
            attribution: "&copy; <a href=\"https://www.amap.com/\">高德地图</a>",
        },
    },
    layers: [{
        id: "amap-tiles",
        type: "raster",
        source: "amap",
        minzoom: 0,
        maxzoom: 18,
    }],
}