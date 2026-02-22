package com.caro.game.controller;

import com.caro.game.entity.Comment;
import com.caro.game.entity.Post;
import com.caro.game.repository.PostRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class HomeController {
    private final PostRepository postRepository;

    public HomeController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("message", "Caro Java (Thymeleaf) is running");
        model.addAttribute("posts", postRepository.findAll());
        return "home/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> apiIndex() {
        return Map.of("message", "Caro Java backend is running", "posts", postRepository.findAll());
    }

    @ResponseBody
    @PostMapping("posts")
    public Post createPost(@RequestBody CreatePostRequest request) {
        Post post = new Post();
        post.setAuthor(request.author());
        post.setContent(request.content());
        post.setImagePath(request.imagePath());
        post.setCreatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    @ResponseBody
    @PostMapping("posts/comment")
    public Map<String, Object> createComment(@RequestBody CreateCommentRequest request) {
        Post post = postRepository.findById(request.postId()).orElseThrow();
        Comment comment = new Comment();
        comment.setAuthor(request.author());
        comment.setContent(request.content());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setPost(post);
        List<Comment> comments = post.getComments();
        comments.add(comment);
        post.setComments(comments);
        postRepository.save(post);
        return Map.of("success", true, "postId", post.getId());
    }

    @GetMapping("multiplayer")
    public String multiplayer() {
        return "home/multiplayer";
    }

    @GetMapping("single-player")
    public String singlePlayer() {
        return "home/single-player";
    }

    public record CreatePostRequest(String content, String author, String imagePath) {
    }

    public record CreateCommentRequest(Long postId, String content, String author) {
    }
}
