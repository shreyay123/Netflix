package com.netflix.contentservice.model;

public enum VideoStatus {
    PENDING,       //MOVIE ADDED BUT NOT UPLOADED YET
    UPLOADED,      //RAW VIDEO UPLOADED
    ENCODING,      //FF MPEG IS ENCOADING
    ENCODED,       // ENCODING COMPLEATED
    READY,         //HLS PLAYLIST READY - CAN BE STREAMED
    FAILED         //ENCOADING FAILED
}
