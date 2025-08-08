metadata {
    definition(name: "Govee Parent Device", namespace: "yourNamespace", author: "Your Name") {
        capability "Refresh"
        command "discoverDevices"
        command "pollAllDevices"
        command "listScenes", ["STRING", "STRING", "STRING"] // deviceId, model, dni
    }
    preferences {
        input name: "apiKey", type: "string", title: "Govee API Key", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "pollInterval", type: "enum", title: "Polling Interval (minutes)", options: ["1", "5", "10", "15", "30"], defaultValue: "5"
        input name: "autoFetchScenes", type: "bool", title: "Automatically fetch scenes on device discovery", defaultValue: true
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    if (logEnable) log.debug "Initializing Govee Parent..."
    if (!state.deviceMap) state.deviceMap = [:]
    discoverDevices()
    schedulePolling()
}

private void schedulePolling() {
    def interval = (pollInterval as Integer) ?: 5
    if (logEnable) log.debug "Scheduling polling every ${interval} minutes"
    runEveryXMinutes(interval, "pollAllDevices")
}

def discoverDevices() {
    def params = [
        uri: "https://openapi.api.govee.com",
        path: "/router/api/v1/user/devices",
        headers: [
            "Govee-API-Key": "${apiKey}",
            "Content-Type": "application/json"
        ]
    ]

    httpGet(params) { resp ->
        if (resp.status == 200 && resp.data?.data?.devices) {
            def newDevices = []
            resp.data.data.devices.each { dev ->
                def id = dev.device
                def model = dev.model
                def name = dev.deviceName
                state.deviceMap[id] = [model: model, name: name]
                def dni = "govee-${id}"
                if (!getChildDevice(dni)) {
                    newDevices << dni
                    addChildDevice("yourNamespace", "Govee Child Device", dni, [label: name])
                }
            }
            if (logEnable && newDevices) log.debug "Discovered new Govee devices: ${newDevices}"
            if (autoFetchScenes) {
                // Fetch scenes for all discovered devices
                state.deviceMap.each { id, info ->
                    def dni = "govee-${id}"
                    listScenes(id, info.model, dni)
                }
            }
        } else {
            log.warn "Govee device discovery failed: ${resp?.status}"
        }
    }
}

def pollAllDevices() {
    state.deviceMap.each { id, info ->
        def dni = "govee-${id}"
        def child = getChildDevice(dni)
        if (!child) return

        def params = [
            uri: "https://openapi.api.govee.com",
            path: "/router/api/v1/device/state",
            headers: [
                "Govee-API-Key": "${apiKey}",
                "Content-Type": "application/json"
            ],
            body: [
                device: id,
                model: info.model
            ]
        ]

        httpPostJson(params) { resp ->
            if (resp.status == 200 && resp.data?.data) {
                child.parseDeviceState(resp.data.data)
            } else {
                log.warn "Failed to poll device ${id}: ${resp?.status}"
            }
        }
    }
}

def listScenes(String deviceId, String model, String dni) {
    def params = [
        uri: "https://openapi.api.govee.com",
        path: "/router/api/v1/device/supportScene",
        headers: [
            "Govee-API-Key": "${apiKey}",
            "Content-Type": "application/json"
        ],
        body: [device: deviceId, model: model]
    ]

    httpPostJson(params) { resp ->
        if (resp.status == 200 && resp.data?.data?.scenes) {
            def scenes = resp.data.data.scenes
            def child = getChildDevice(dni)
            if (child) {
                child.updateAvailableScenes(scenes)
            }
        } else {
            log.warn "Failed to retrieve scenes: ${resp?.status}"
        }
    }
}

def sendCommand(String deviceId, String model, Object cmd, Boolean isSegment = false) {
    def path = "/router/api/v1/device/control"
    def body = [
        device: deviceId,
        model: model,
        cmd: [:]
    ]

    if (isSegment) {
        body.cmd = cmd
    } else if (cmd instanceof Map) {
        body.cmd = [
            name : cmd.name,
            value: cmd.value
        ]
    } else {
        log.warn "Unsupported command format: ${cmd}"
        return
    }

    def params = [
        uri: "https://openapi.api.govee.com",
        path: path,
        headers: [
            "Govee-API-Key": "${apiKey}",
            "Content-Type": "application/json"
        ],
        body: body
    ]

    if (logEnable) log.debug "Sending command to ${deviceId}: ${body}"

    httpPostJson(params) { resp ->
        if (resp.status == 200) {
            if (logEnable) log.debug "Command sent successfully"
        } else {
            log.warn "Failed to send command: ${resp.status} - ${resp.data}"
        }
    }
}