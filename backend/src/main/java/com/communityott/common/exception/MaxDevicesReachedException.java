package com.communityott.common.exception;

import com.communityott.device.dto.DeviceResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class MaxDevicesReachedException extends ApiException {

    private final List<DeviceResponse> activeDevices;

    public MaxDevicesReachedException(List<DeviceResponse> activeDevices) {
        super("Maximum registered device limit (2) reached. Please replace an existing registered device to continue.",
                HttpStatus.CONFLICT, "MAX_DEVICES_REACHED");
        this.activeDevices = activeDevices;
    }
}
