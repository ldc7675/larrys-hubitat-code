metadata {
    definition (name: "TestDriver", namespace: "yourname", author: "you") {
        capability "Actuator"
    }
}

def installed() {
    log.debug "Installed"
}

def updated() {
    log.debug "Updated"
}
