package com.connecthub.media.resource;

import com.connecthub.media.client.RoomServiceClient;
import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.service.MediaService;
import com.connecthub.media.service.MediaSessionService;
import com.connecthub.media.storage.LocalDiskStorageProvider;
import com.connecthub.media.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** File serving: signed media session + room membership, real local-disk storage. */
class MediaFileServingTest {

    @TempDir Path tmp;
    private MediaService svc;
    private RoomServiceClient rooms;
    private MediaSessionService sessions;
    private StorageProvider storage;
    private MediaResource resource;

    @BeforeEach
    void setUp() throws Exception {
        svc = mock(MediaService.class);
        rooms = mock(RoomServiceClient.class);
        sessions = mock(MediaSessionService.class);
        storage = new LocalDiskStorageProvider(tmp.resolve("store").toString());
        resource = new MediaResource(svc, rooms, sessions, storage);
        when(sessions.verify("good-user-7")).thenReturn(Optional.of(7));
        when(sessions.verify("good-user-8")).thenReturn(Optional.of(8));
        when(rooms.isMember("room1", 7)).thenReturn(true);
        when(rooms.isMember("room1", 8)).thenReturn(false);
    }

    private void store(String key, byte[] bytes) throws Exception {
        Path src = Files.createTempFile(tmp, "s", ".bin");
        Files.write(src, bytes);
        storage.put(key, src);
    }

    private MediaFile file(String id, String room, String key, String name, String mime, boolean thumb) {
        MediaFile f = MediaFile.builder().mediaId(id).roomId(room).filename(key).originalName(name)
                .mimeType(mime).thumbnailUrl(thumb ? "x/thumb" : null).uploaderId(1).url("u").build();
        when(svc.getById(id)).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void noOrInvalidSession_is401_evenForExistingFiles() throws Exception {
        store("files/u/a.txt", "hi".getBytes());
        file("m1", "room1", "files/u/a.txt", "a.txt", "text/plain", false);
        assertEquals(HttpStatus.UNAUTHORIZED, resource.file("m1", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, resource.file("m1", "forged").getStatusCode());
        verify(svc, never()).getById(anyString());
    }

    @Test
    void member_getsBytes_withHardenedHeaders() throws Exception {
        store("images/u/p.png", new byte[]{1, 2, 3});
        file("m2", "room1", "images/u/p.png", "p.png", "image/png", true);

        ResponseEntity<Resource> r = resource.file("m2", "good-user-7");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("image/png", r.getHeaders().getContentType().toString());
        assertEquals("nosniff", r.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(r.getHeaders().getFirst("Content-Security-Policy").contains("sandbox"));
        assertTrue(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
        assertTrue(r.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL).contains("private"));
        assertEquals(3, r.getBody().contentLength());
    }

    @Test
    void nonMember_isForbidden_soGuessingAnIdRevealsNothing() throws Exception {
        store("files/u/secret.txt", "secret".getBytes());
        file("m3", "room1", "files/u/secret.txt", "secret.txt", "text/plain", false);
        assertEquals(HttpStatus.FORBIDDEN, resource.file("m3", "good-user-8").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, resource.thumbnail("m3", "good-user-8").getStatusCode());
    }

    @Test
    void roomServiceDown_failsClosed() throws Exception {
        store("files/u/a.txt", "hi".getBytes());
        file("m4", "room1", "files/u/a.txt", "a.txt", "text/plain", false);
        when(rooms.isMember(anyString(), anyInt())).thenThrow(new RuntimeException("down"));
        assertEquals(HttpStatus.FORBIDDEN, resource.file("m4", "good-user-7").getStatusCode());
    }

    @Test
    void documents_areAttachments_notInline() throws Exception {
        store("files/u/report.pdf", "%PDF".getBytes());
        file("m5", "room1", "files/u/report.pdf", "report.pdf", "application/pdf", false);
        String cd = resource.file("m5", "good-user-7").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(cd.startsWith("attachment"), cd);
    }

    @Test
    void avatars_haveNoRoom_andAreVisibleToAnySignedInUser() throws Exception {
        store("avatars/u/me.png", new byte[]{9});
        file("av1", null, "avatars/u/me.png", "me.png", "image/png", false);
        assertEquals(HttpStatus.OK, resource.file("av1", "good-user-8").getStatusCode());
        verifyNoInteractions(rooms);
    }

    @Test
    void thumbnail_servedAsJpeg_andMissingThumbIs404() throws Exception {
        store("images/u/p.png", new byte[]{1});
        store("images/u/thumb_p.png", new byte[]{2, 2});
        file("m6", "room1", "images/u/p.png", "p.png", "image/png", true);
        file("m7", "room1", "images/u/p.png", "p.png", "image/png", false);

        ResponseEntity<Resource> t = resource.thumbnail("m6", "good-user-7");
        assertEquals(HttpStatus.OK, t.getStatusCode());
        assertEquals("image/jpeg", t.getHeaders().getContentType().toString());
        assertEquals(2, t.getBody().contentLength());
        assertEquals(HttpStatus.NOT_FOUND, resource.thumbnail("m7", "good-user-7").getStatusCode());
    }

    @Test
    void unknownId_and_missingBytesOnDisk_are404() throws Exception {
        when(svc.getById("nope")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, resource.file("nope", "good-user-7").getStatusCode());
        file("m8", "room1", "files/u/gone.txt", "gone.txt", "text/plain", false); // row exists, bytes don't
        assertEquals(HttpStatus.NOT_FOUND, resource.file("m8", "good-user-7").getStatusCode());
    }

    @Test
    void startSession_setsHttpOnlyLaxCookieScopedToFilePath() {
        when(sessions.issue(7)).thenReturn("tok-7");
        ResponseEntity<java.util.Map<String, Object>> r = resource.startSession(7);
        String cookie = r.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith("ch_media=tok-7"), cookie);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/media/file"));
        assertTrue(cookie.contains("Max-Age=1800"));
    }
}
