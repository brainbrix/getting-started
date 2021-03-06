package com.github.database.rider.gettingstarted;

import com.github.database.rider.cdi.api.DBUnitInterceptor;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.util.EntityManagerProvider;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by pestano on 16/07/16.
 */
@RunWith(CdiTestRunner.class)
@DBUnitInterceptor
public class ToManyAssociationListTest {
    
    
     @Inject
     UserRepository userRepository;
     
     @Inject 
     EntityManager em;
    

    @Test
    @DataSet("userTweets.yml")
    public void shouldListUsersAndTweetsWithJPQL() {

        long count = (Long) EntityManagerProvider.em().createQuery("select count (distinct u.id) from User u " +
                "left join u.tweets t where t.content like '%tweet%'").
                getSingleResult();
        assertThat(count).isEqualTo(3);

        List<User> users = EntityManagerProvider.em().createQuery
                ("select distinct u from User u " +
                        "left join u.tweets t where t.content like '%tweet%' ", User.class).
            /* not working   ("select distinct new com.github.dbunit.rules.sample.User(u.id, u.name, t.id, t.content, t.likes) from User u " +
                        "left join u.tweets t where t.content like '%tweet%'", User.class).*/
                setFirstResult(0).setMaxResults(2).
                getResultList();
        assertThat(users).isNotNull().hasSize(2);
        assertThat(users.get(0)).hasFieldOrPropertyWithValue("name","@dbunit");
        assertThat(users.get(1)).hasFieldOrPropertyWithValue("name","@dbunit2");
        assertThat(users.get(0).getTweets()).isNotNull().hasSize(2);
        assertThat(users.get(1).getTweets()).isNotNull().hasSize(2);

    }

    @Test
    @DataSet("userTweets.yml")
    @Ignore("Hibernate criteria is not working for this case and is not recommended anymore, see: https://twitter.com/realpestano/status/754720913933950976")
    public void shouldListUsersAndTweetsWithHibernateCriteria() {

        Session session = EntityManagerProvider.em().unwrap(Session.class);
        Criteria criteria = session.createCriteria(User.class);

        long count = (Long)criteria.createAlias("tweets","t", JoinType.LEFT_OUTER_JOIN).
        add(Restrictions.ilike("t.content", "tweet", MatchMode.ANYWHERE)).
        setProjection(Projections.countDistinct("id")).
        uniqueResult();

        assertThat(count).isEqualTo(3);


        ProjectionList projectionList = Projections.projectionList().
                add(Projections.id().as("id")).
                add(Projections.property("name").as("name")).
                add(Projections.property("t.id").as("tweets.id")).
                add(Projections.property("t.content").as("tweets.content")).
                add(Projections.property("t.likes").as("tweets.likes"));

        /*
       List<Long> usersIds = criteria.setProjection(Projections.distinct(Projections.id())).
                setFirstResult(0).setMaxResults(2).
                list();*/

        List<User> users = criteria.setProjection(Projections.distinct(projectionList)).
                 //hibernate's alisToBean throws PropertyNotFoundException: Could not find setter for tweets.id on class com.github.dbunit.rules.sample.User
                 //setResultTransformer(new AliasToBeanResultTransformer(User.class)).
                setResultTransformer(new AliasToBeanNestedResultTransformer(User.class)).
                setFirstResult(0).setMaxResults(2).
                list();
        assertThat(users).isNotNull().hasSize(2);
        assertThat(users.get(0)).hasFieldOrPropertyWithValue("name","@dbunit");
        //fails cause resultTransformer resolves entity values in-memory ater the page has been returned from db
        assertThat(users.get(1)).hasFieldOrPropertyWithValue("name","@dbunit2");
        assertThat(users.get(0).getTweets()).isNotNull().hasSize(2);
        assertThat(users.get(0).getTweets().get(0).getDate()).isNull();//not part of select
        assertThat(users.get(1).getTweets()).isNotNull().hasSize(2);

    }
    
    @Test
    @DataSet("userTweets.yml")
    public void shouldListUsersAndTweetsWithJPACriteria() {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<User> query = builder.createQuery(User.class);
        Root<User> root = query.from(User.class);
        Join<User, Tweet> join = root.join(User_.tweets, javax.persistence.criteria.JoinType.LEFT);
        CriteriaQuery<User> select = query.
                where(builder.like(builder.lower(join.get(Tweet_.content)), "%tweet%")).
                distinct(true).
                select(root);
               //multiselect(root.get(User_.id),root.get(User_.name), join.get(Tweet_.id), join.get(Tweet_.content), join.get(Tweet_.likes));
        List<User> users = em.createQuery(select).setFirstResult(0).setMaxResults(2).getResultList();
        
        assertThat(users).isNotNull().hasSize(2);
        assertThat(users.get(0)).hasFieldOrPropertyWithValue("name","@dbunit");
        assertThat(users.get(1)).hasFieldOrPropertyWithValue("name","@dbunit2");
        assertThat(users.get(0).getTweets()).isNotNull().hasSize(2);
        assertThat(users.get(1).getTweets()).isNotNull().hasSize(2);
    }

    
    @Test
    @DataSet("userTweets.yml")
    public void shouldListUsersAndTweetsWithDesltaSpikeCriteria() {
        //the query below should be in user repository, is here for comparison purposes3
        List<User> users = userRepository.criteria().
        //select(User.class,userRepository.attribute(User_.id), userRepository.attribute(User_.name)).
        join(User_.tweets, 
                userRepository.where(Tweet.class,javax.persistence.criteria.JoinType.LEFT).
                likeIgnoreCase(Tweet_.content, "%tweet%")).
        distinct().createQuery().
        setFirstResult(0).setMaxResults(2).
        getResultList();
        assertThat(users).isNotNull().hasSize(2);
        assertThat(users.get(0)).hasFieldOrPropertyWithValue("name","@dbunit");
        assertThat(users.get(1)).hasFieldOrPropertyWithValue("name","@dbunit2");
        assertThat(users.get(0).getTweets()).isNotNull().hasSize(2);
        assertThat(users.get(1).getTweets()).isNotNull().hasSize(2);
    }

    
    

}
