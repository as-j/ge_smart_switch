/*
    GE Smart Plug

    Changes:
        - 09/26/19 Update nname and fingerprint from @aruffell
        - 09/27/19 Fix typo for reporting, and update fingerprint order, fix whitespace
*/

import groovy.transform.Field

@Field static Map zigbeeSwitch = [
    cluster: [
        name: "Switch Cluster",
        hexString: "0006",
        hexValue: 0x0006,
    ],
    onOffAttr : [
        name: "On/Off",
        hexString: "0000",
        hexValue: 0x0000,
    ],
]

@Field static Map zigbeeSimpleMonitoring = [
    cluster: [
        name: "Simple Monitoring Cluster",
        hexString: "0702",
        hexValue: 0x0702,
    ],
    energyAttr : [
        name: "Accumulated Energy Used",
        hexString: "0000",
        hexValue: 0x0000,
        divisor: 10000,
    ],
    powerAttr : [
        name: "Instantaneous Power Use",
        hexString: "0400",
        hexValue: 0x0400,
        divisor: 10,
    ],
]

@Field static List powerChangeOptions = [
    "No Reports",
    "1 Watt",
    "2 Watts",
    "3 Watts",
    "4 Watts",
    "5 Watts",
    "10 Watts",
    "25 Watts",
    "50 Watts",
    "100 Watts",
    "200 Watts",
    "500 Watts",
    "1000 Watts",
]

@Field static List energyChangeOptions = [
    "No Reports",
    "0.001 kWh",
    "0.005 kWh",
    "0.010 kWh",
    "0.025 kWh",
    "0.050 kWh",
    "0.100 kWh",
    "0.250 kWh",
    "0.500 kWh",
    "1 kWh",
    "2 kWh",
    "5 kWh",
    "10 kWh",
    "20 kWh",
]

@Field static List timeReportOptions = [
    "No Reports",
    "5 Seconds",
    "10 Seconds",
    "30 Seconds",
    "1 Minute",
    "5 Minutes",
    "15 Minutes",
    "30 Minutes",
    "1 Hour",
    "6 Hours",
    "12 Hours",
    "24 Hours",
]

metadata {
    definition (name: "GE/Jasco Smart Switch", namespace: "asj", author: "asj") {
        capability "Configuration"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "Sensor"
        capability "Outlet"
        capability "Switch"

        command "resetEnergy"

        // GE/Jasco
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,0702", outClusters: "0003,000A,0019", manufacturer: "Jasco", model: "45853", deviceJoinName: "GE ZigBee Plug-In Switch"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,0702", outClusters: "000A,0019", manufacturer: "Jasco", model: "45856", deviceJoinName: "GE ZigBee In-Wall Switch"
    }

    preferences {
        //standard logging options
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "powerChange", type: "enum", title: "Power Report Value Change:", defaultValue: powerChangeOptions[0], options: powerChangeOptions
        input name: "powerReport", type: "enum", title: "Power Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        input name: "energyChange", type: "enum", title: "Energy Report Value Change:", defaultValue: energyChangeOptions[0], options: energyChangeOptions
        input name: "energyReport", type: "enum", title: "Energy Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
    if (logEnable) log.debug "description is ${description}"
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
    if (description.startsWith("catchall")) return

    def cluster = descMap.clusterId ?: descMap.cluster
    def hexValue = descMap.value
    def attrId = descMap.attrId
    def encoding = descMap.encoding
    def size = descMap.size

    switch (cluster){
        case zigbeeSwitch.cluster.hexString: //switch
            switch (attrId) {
                case zigbeeSwitch.onOffAttr.hexString:
                    getSwitchResult(hexValue)
                    break
            }
            break
        case zigbeeSimpleMonitoring.cluster.hexString:
            //if (hexValue) log.info "power cluster: ${cluster} ${attrId} encoding: ${encoding} size: ${size} value: ${hexValue} int: ${hexStrToSignedInt(hexValue)}"
            switch (attrId) {
                case zigbeeSimpleMonitoring.energyAttr.hexString:
                    getEnergyResult(hexValue)
                    break
                case zigbeeSimpleMonitoring.powerAttr.hexString:
                    getPowerResult(hexValue)
                    break
            }
            break
        default :
            if (hexValue) log.info "unknown cluster: ${cluster} ${attrId} encoding: ${encoding} size: ${size} value: ${hexValue} int: ${hexStrToSignedInt(hexValue)}"
            break
    }
    return
}



//event methods
private getPowerResult(hex){
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeSimpleMonitoring.powerAttr.divisor
    def name = "power"
    def unit = "W"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getEnergyResult(hex){
    def value = hexStrToSignedInt(hex)
    if (state.energyReset) {
        state.energyReset = null
        state.energyResetValue = value
    }
    if (state.energyResetValue) {
        if (value < state.energyResetValue) {
            state.energyResetValue = null
        } else {
            value -= state.energyResetValue
        }
    }

    value = value / zigbeeSimpleMonitoring.energyAttr.divisor
    def name = "energy"
    def unit = "kWh"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getSwitchResult(hex){
    def value = hexStrToSignedInt(hex) == 1 ? "on" : "off"
    def name = "switch"
    def unit = ""
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getChangeValue(change) {
    def changeValue
    def prMatch = (change =~ /([0-9.]+) /)
    if (prMatch) changeValue = prMatch[0][1]
    if (changeValue.isInteger()) {
        changeValue = changeValue.toInteger()
    } else if(changeValue.isDouble()) {
        changeValue = changeValue.toDouble()
    }
    return changeValue
}

private getReportValue(report) {
    def reportValue
    def prMatch = (report =~ /(\d+) Seconds/)
    if (prMatch) reportValue = prMatch[0][1].toInteger()
    prMatch = (report =~ /(\d+) Minute/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 60
    prMatch = (report =~ /(\d+) Hour/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 3600

    return reportValue
}

//capability and device methods
def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
   log.debug "Refresh"

   List cmds =  []
   def attrs = [zigbeeSimpleMonitoring.energyAttr.hexValue, 0x200, zigbeeSimpleMonitoring.powerAttr.hexValue]
   attrs.each { it ->
        cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,it,[:],200) 
   }
   // Ask for units of measure, divisor, multiplier, and type
   //for (def base = 0x300; base < 0x30F; base++) {
   //    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,base,[:],200) 
   //}
   return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    runIn(1800,logsOff)

    List cmds = []
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.powerAttr.hexValue,
                                            DataType.INT24,
                                            5,
                                            getReportValue(powerReport),
                                            getChangeValue(powerChange) * zigbeeSimpleMonitoring.powerAttr.divisor)
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.energyAttr.hexValue,
                                            DataType.UINT48, 
                                            5,
                                            getReportValue(energyReport),
                                            (getChangeValue(energyChange) * zigbeeSimpleMonitoring.energyAttr.divisor).toInteger())
    cmds = cmds + refresh()
    if (logEnabled) log.info "cmds:${cmds}"
    return cmds
}

def getReadings() {
    List cmds = []
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.powerAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.energyAttr.hexValue,
                                  [:],20) 

    return cmds
}

def updated() {
    log.trace "Updated()"
    log.trace "powerChangeValue: " + getChangeValue(powerChange)
    log.trace "powerReportvalue: " + getReportValue(powerReport)
    log.trace "energyChangeValue: " + getChangeValue(energyChange)
    log.trace "energyReportvalue: " + getReportValue(energyReport)
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff) 
    return configure()
}


def resetEnergy() {
    state.energyReset = true
    return getReadings()
}
