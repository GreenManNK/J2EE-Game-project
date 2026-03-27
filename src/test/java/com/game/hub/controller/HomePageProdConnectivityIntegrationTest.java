package com.game.hub.controller;

import com.game.hub.entity.Comment;
import com.game.hub.entity.Post;
import com.game.hub.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomePageProdConnectivityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @BeforeEach
    void resetPosts() {
        postRepository.deleteAll();
    }

    @Test
    void homePageShouldRenderWhenPostHasLazyCommentsAndOpenInViewIsDisabled() throws Exception {
        Post post = new Post();
        post.setAuthor("Alice");
        post.setAuthorUserId("alice-1");
        post.setContent("Hello public players");
        post.setCreatedAt(LocalDateTime.now());
        post.setComments(new ArrayList<>());

        Comment comment = new Comment();
        comment.setAuthor("Bob");
        comment.setContent("Joined from outside");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setPost(post);
        post.getComments().add(comment);

        postRepository.save(post);

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/index"))
            .andExpect(content().string(containsString("Hello public players")))
            .andExpect(content().string(containsString("Joined from outside")));
    }
}
