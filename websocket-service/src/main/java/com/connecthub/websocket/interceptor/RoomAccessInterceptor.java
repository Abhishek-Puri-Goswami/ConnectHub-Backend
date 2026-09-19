package com.connecthub.websocket.interceptor;

import com.connecthub.websocket.service.RoomAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorizes STOMP frames after {@link JwtChannelInterceptor} has authenticated the session:
 * <ul>
 *   <li>every frame other than CONNECT/DISCONNECT/UNSUBSCRIBE needs an authenticated principal;</li>
 *   <li>SUBSCRIBE to {@code /topic/room/{roomId}[/...]} requires membership of that room;</li>
 *   <li>SUBSCRIBE to {@code /user/{id}/...} requires {@code id} to be the caller's own id;</li>
 *   <li>global topics ({@code /topic/presence}, {@code /topic/broadcast}) are open to any authenticated user;</li>
 *   <li>any other {@code /topic} destination is refused.</li>
 * </ul>
 * Rejection throws, which Spring turns into a STOMP ERROR frame.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomAccessInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/([^/]+)(/.*)?$");
    private static final Pattern USER_DEST = Pattern.compile("^/user/(\\d+)/.*$");

    private final RoomAccessService roomAccess;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        StompCommand cmd = accessor.getCommand();
        if (cmd == StompCommand.CONNECT || cmd == StompCommand.STOMP || cmd == StompCommand.DISCONNECT
                || cmd == StompCommand.UNSUBSCRIBE) return message;

        Principal user = accessor.getUser();
        if (user == null) throw new IllegalStateException("Not authenticated");

        if (cmd == StompCommand.SUBSCRIBE) authorizeSubscribe(accessor.getDestination(), user);
        return message;
    }

    private void authorizeSubscribe(String dest, Principal user) {
        if (dest == null) throw new IllegalStateException("Missing destination");
        Matcher room = ROOM_TOPIC.matcher(dest);
        if (room.matches()) {
            if (!roomAccess.isMember(room.group(1), parseId(user.getName()))) {
                log.warn("Denied SUBSCRIBE {} for user {}", dest, user.getName());
                throw new IllegalStateException("Not a member of this room");
            }
            return;
        }
        Matcher u = USER_DEST.matcher(dest);
        if (u.matches()) {
            if (!u.group(1).equals(user.getName())) {
                log.warn("Denied SUBSCRIBE {} for user {}", dest, user.getName());
                throw new IllegalStateException("Cannot subscribe to another user queue");
            }
            return;
        }
        if (dest.equals("/topic/presence") || dest.equals("/topic/broadcast")) return;
        if (dest.startsWith("/topic")) {
            log.warn("Denied SUBSCRIBE {} for user {}", dest, user.getName());
            throw new IllegalStateException("Unknown topic");
        }
        // /user/queue/... and /queue/... are per-session (Spring appends the session id)
    }

    private static int parseId(String name) {
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
