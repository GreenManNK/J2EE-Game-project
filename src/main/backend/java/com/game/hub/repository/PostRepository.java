package com.game.hub.repository;

import com.game.hub.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("""
        select distinct p
        from Post p
        left join fetch p.comments
        order by p.createdAt desc, p.id desc
        """)
    List<Post> findAllWithComments();

    @Query("""
        select distinct p
        from Post p
        left join fetch p.comments
        where p.id = :postId
        """)
    Optional<Post> findByIdWithComments(@Param("postId") Long postId);
}
