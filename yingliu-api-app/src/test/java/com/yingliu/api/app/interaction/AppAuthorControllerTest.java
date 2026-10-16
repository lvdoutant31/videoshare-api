package com.yingliu.api.app.interaction;

import com.yingliu.api.app.content.AppFeedAssembler;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.VideoAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppAuthorControllerTest {
    private final AppInteractionService service = mock(AppInteractionService.class);
    private final VideoAppService videos = mock(VideoAppService.class);
    private final AppActorProvider actor = mock(AppActorProvider.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AppInteractionController(service, videos, actor, new AppFeedAssembler())).build();

    @Test
    void publicSearchPassesKeywordCursorAndOptionalViewer() throws Exception {
        when(actor.optionalUserId()).thenReturn(null);
        when(service.searchAuthors("admin", "99", 20, null)).thenReturn(new CursorPage<>(List.of(
                new AppInteractionService.AuthorSearchView("2097975613254594562", "admin", null, 27L, false)), null));
        mvc.perform(get("/api/v1/app/users").param("keyword", "admin").param("cursor", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value("2097975613254594562"))
                .andExpect(jsonPath("$.data.items[0].worksCount").value(27));
        verify(actor, never()).currentUserId();
    }

    @Test
    void authorLikesUseTheAuthorsRecordsAndTheVisitorsInteractionState() throws Exception {
        when(actor.optionalUserId()).thenReturn(9L);
        when(service.listLikeVideoIds(7L, null, 20)).thenReturn(new CursorPage<>(List.of("88"), null));
        when(videos.getPublicVideo(88L, 9L)).thenReturn(new VideoAppService.VideoDetail(88L, 6L, "other author", "liked content",
                "<p>body</p>", null, null, null, List.of(), List.of(), new VideoAppService.Stats(0, 2, 0, 0, 0),
                LocalDateTime.of(2026, 10, 12, 8, 0), new VideoAppService.ViewerState(false, false), false));
        mvc.perform(get("/api/v1/app/users/7/likes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("liked content"))
                .andExpect(jsonPath("$.data.items[0].viewerState.liked").value(false));
        verify(service).getAuthorProfile(7L, 9L);
        verify(service, never()).listLikeVideoIds(9L, null, 20);
    }
}
