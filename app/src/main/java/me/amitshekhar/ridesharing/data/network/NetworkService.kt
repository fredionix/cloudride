package me.amitshekhar.ridesharing.data.network

import me.amitshekhar.ridesharing.simulator.WebSocket
import me.amitshekhar.ridesharing.simulator.WebSocketListener

class NetworkService {
//untuk apa ini?
    fun createWebSocket(webSocketListener: WebSocketListener): WebSocket {
        return WebSocket(webSocketListener)
    }

}