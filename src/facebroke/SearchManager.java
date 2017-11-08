package facebroke;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import facebroke.model.User;
import facebroke.util.HibernateUtility;
import facebroke.util.ValidationSnipets;


/**
 * Servlet to handle the /search endpoint.
 * 
 * Basically, this will handle search requests and render a results
 * page out to the requesting user
 * 
 * @author matt @ Software Secured
 */
@WebServlet("/search")
public class SearchManager extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final static Logger log = LoggerFactory.getLogger(SearchManager.class);

	
	/**
	 * Call parent servlet
	 */
    public SearchManager() {
        super();
    }


    /**
     * Simple shim to pass GET requests to handleSearch
     */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		handleSearch(req, res);
	}


	/**
     * Simple shim to pass POST requests to handleSearch
     */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		handleSearch(req, res);
	}

	
	private void handleSearch(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		// If not valid session, send user to registration page
		if(!ValidationSnipets.isValidSession(req.getSession())){
			res.sendRedirect("register");
			return;
		}
		
		String queryString;
		
		queryString = req.getParameter("q");
		
		if(queryString == null || queryString.length() < 3) {
			// Pass a results object to JSTL to handle
			req.setAttribute("rows", new ArrayList<User>());
			
			// Forward to JSP to handle
			req.getRequestDispatcher("search_results.jsp").forward(req, res);
		}

		Session sess = HibernateUtility.getSessionFactory().openSession();
		List<User> result = sess.createSQLQuery("select * from Users WHERE ID = \'%" + queryString + "%\'").addEntity(User.class).list();
		


		/*FullTextSession fts = Search.getFullTextSession(HibernateUtility.getSessionFactory().openSession());
		
		fts.beginTransaction();
		
		QueryBuilder qb = fts.getSearchFactory()
							 .buildQueryBuilder()
							 .forEntity(User.class)
							 .get();
		
		Query query = qb.keyword()
						.onFields("fname","lname","username")
						.matching(queryString)
						.createQuery();
		
		//Hibernate Query wrapper
		FullTextQuery hibQuery = fts.createFullTextQuery(query, User.class);
		
		@SuppressWarnings("unchecked")
		List<User> result = hibQuery.getResultList();*/
		
		log.info("Got {} results for \'{}\'",result.size(),ValidationSnipets.sanitizeCRLF(queryString));
		
		
		// Pass a results object to JSTL to handle
		req.setAttribute("user_rows", result);
		
		// Forward to JSP to handle
		req.getRequestDispatcher("search_results.jsp").forward(req, res);
		
		//fts.getTransaction().commit();
		//fts.close();
	}
}
