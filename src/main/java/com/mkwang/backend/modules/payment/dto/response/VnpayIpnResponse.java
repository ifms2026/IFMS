package com.mkwang.backend.modules.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {
    public static VnpayIpnResponse ok()         { return new VnpayIpnResponse("00", "Confirm Success"); }
    public static VnpayIpnResponse invalidSig() { return new VnpayIpnResponse("97", "Invalid Checksum"); }
    public static VnpayIpnResponse notFound()   { return new VnpayIpnResponse("01", "Order not Found"); }
    public static VnpayIpnResponse alreadyDone(){ return new VnpayIpnResponse("02", "Order already confirmed"); }
}
