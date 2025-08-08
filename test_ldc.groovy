metadata {
    definition(name: "ldc Govee Child Device", namespace: "yourNamespace", author: "Your Name") {
        capability "Switch"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "Refresh"

        command "setGoveeScene", ["string"]
        command "setSegmentColor", ["LIST", "MAP"]
        command "setSegmentBrightness", ["LIST", "NUMBER"]
        command "setSegmentColorTemp", ["LIST", "NUMBER"]
    }

    preferences {
        input name: "logEnableChild", type: "bool", title: "Enable debug logging (child)", defaultValue: true
        input name: "autoFetchScenesChild", type: "bool", title: "Auto fetch scenes on refresh", defaultValue: true
    }
}

def updated() {
    if (logEnableChild) log.debug "${device.displayName} preferences updated"
    if (autoFetchScenesChild) {
        getAvailableScenes()
    }
}

def on() {
    sendGoveeCmd("turn", [value: "on"])
}

def off() {
    sendGoveeCmd("turn", [value: "off"])
}

def setLevel(level) {
    sendGoveeCmd("brightness", [value: level.toInteger()])
    sendEvent(name: "level", value: level)
}

def setColor(Map color) {
    def hue = color.hue as float
    def sat = color.saturation as float
    def lvl = color.level ?: 100
    def rgb = hslToRgb(hue, sat, lvl)
    sendGoveeCmd("color", [value: rgb])
    sendEvent(name: "color", value: colorUtil.rgbToHex(rgb))
    sendEvent(name: "hue", value: hue)
    sendEvent(name: "saturation", value: sat)
    sendEvent(name: "level", value: lvl)
}

def setColorTemperature(temp) {
    sendGoveeCmd("colorTem", [value: temp.toInteger()])
    sendEvent(name: "colorTemperature", value: temp)
}

def setGoveeScene(String sceneName) {
    sendGoveeCmd("scene", [value: sceneName])
}

def setSegmentColor(List segmentIds, Map colorMap) {
    // Convert segment IDs to integer array
    def segmentIntList = segmentIds.collect { it.toString().toInteger() }
    def rgb = hslToRgb(colorMap.hue as float, colorMap.saturation as float, colorMap.level ?: 100)
    def rgbInt = (rgb.r << 16) | (rgb.g << 8) | rgb.b
    sendSegmentCmd("segmentedColorRgb", [segment: segmentIntList, rgb: rgbInt])
}

def setSegmentBrightness(List segmentIds, Number brightness) {
    def segmentIntList = segmentIds.collect { it.toString().toInteger() }
    sendSegmentCmd("segmentedBrightness", [segment: segmentIntList, brightness: brightness.toInteger()])
}

def setSegmentColorTemp(List segmentIds, Number kelvin) {
    def segmentIntList = segmentIds.collect { it.toString().toInteger() }
    sendSegmentCmd("segmentedColorTem", [segment: segmentIntList, colorTemInKelvin: kelvin.toInteger()])
}

def refresh() {
    if (logEnableChild) log.debug "Refreshing ${device.displayName}"
    parent?.pollAllDevices()
    if (autoFetchScenesChild) {
        getAvailableScenes()
    }
}

private sendGoveeCmd(String cmd, Map valueMap) {
    def deviceId = device.deviceNetworkId.replace("govee-", "")
    def model = parent?.state?.deviceMap[deviceId]?.model
    if (!deviceId || !model) return
    if (logEnableChild) log.debug "Sending command: ${cmd} with value ${valueMap.value} for device ${deviceId}"
    parent?.sendCommand(deviceId, model, [name: cmd, value: valueMap.value])
}

private sendSegmentCmd(String instance, Map valueMap) {
    def deviceId = device.deviceNetworkId.replace("govee-", "")
    def model = parent?.state?.deviceMap[deviceId]?.model
    if (!deviceId || !model) return
    def cmd = [
        type: "devices.capabilities.segment_color_setting",
        instance: instance,
        value: valueMap
    ]
    if (logEnableChild) log.debug "Sending segment command: ${instance} with value ${valueMap} for device ${deviceId}"
    parent?.sendCommand(deviceId, model, cmd, true)
}

def parseDeviceState(Map data) {
    data?.properties?.each { prop ->
        switch (prop?.type) {
            case "powerState":
                sendEvent(name: "switch", value: prop.value == "on" ? "on" : "off")
                break
            case "brightness":
                sendEvent(name: "level", value: prop.value as int)
                break
            case "color":
                def r = prop.value.r
                def g = prop.value.g
                def b = prop.value.b
                def hex = colorUtil.rgbToHex([r: r, g: g, b: b])
                sendEvent(name: "color", value: hex)
                break
            case "colorTem":
                sendEvent(name: "colorTemperature", value: prop.value as int)
                break
            default:
                if (logEnableChild) log.debug "Unhandled property: ${prop?.type}"
        }
    }
}

def getAvailableScenes() {
    def deviceId = device.deviceNetworkId.replace("govee-", "")
    def model = parent?.state?.deviceMap[deviceId]?.model
    if (model) {
        parent?.listScenes(deviceId, model, device.deviceNetworkId)
    } else if (logEnableChild) {
        log.warn "Model info missing for device ${deviceId}"
    }
}

def updateAvailableScenes(List scenes) {
    sendEvent(name: "availableScenes", value: groovy.json.JsonOutput.toJson(scenes))
}

// HSL to RGB conversion for Hubitat → Govee
private Map hslToRgb(float h, float s, float l) {
    h = (h % 100) / 100.0
    s = s / 100.0
    l = l / 100.0

    def q = l < 0.5 ? (l * (1 + s)) : (l + s - l * s)
    def p = 2 * l - q

    def r = hueToRgb(p, q, h + 1.0 / 3)
    def g = hueToRgb(p, q, h)
    def b = hueToRgb(p, q, h - 1.0 / 3)

    return [r: (r * 255).round(), g: (g * 255).round(), b: (b * 255).round()]
}

private float hueToRgb(p, q, t) {
    if (t < 0) t += 1
    if (t > 1) t -= 1
    if (t < 1.0 / 6) return p + (q - p) * 6 * t
    if (t < 1.0 / 2) return q
    if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6
    return p
}
