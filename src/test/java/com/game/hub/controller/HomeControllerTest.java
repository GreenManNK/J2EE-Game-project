package com.game.hub.controller;

import com.game.hub.entity.Post;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PostRepository;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void createPostShouldRequireLoginSession() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Object result = controller.createPost(new HomeController.CreatePostRequest("hello", "spoof", ""), request);

        assertTrue(result instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) result).get("success"));
        assertEquals("Login required", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void createPostShouldUseSessionUserDisplayNameInsteadOfClientAuthor() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setDisplayName("Alice");
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Object result = controller.createPost(new HomeController.CreatePostRequest("  Xin chao  ", "Spoofed", ""), request);

        assertTrue(result instanceof Map<?, ?>);
        assertTrue((Boolean) ((Map<?, ?>) result).get("success"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertEquals("Alice", saved.getAuthor());
        assertEquals("Xin chao", saved.getContent());
    }

    @Test
    void createCommentShouldRequireLoginSession() {
        PostRepository postRepository = mock(PostRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        HomeController controller = new HomeController(postRepository, userAccountRepository);
        Map<String, Object> result = controller.createComment(new HomeController.CreateCommentRequest(1L, "Hi", "spoof"), request);

        assertFalse(result.get("success") instanceof Boolean b && b);
        assertEquals("Login required", result.get("error"));
    }
}
