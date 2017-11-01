package facebroke;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import facebroke.model.Image;
import facebroke.model.User;
import facebroke.model.User.UserRole;
import facebroke.util.FacebrokeException;
import facebroke.util.HibernateUtility;
import facebroke.util.ValidationSnipets;


/**
 * Servlet to handle the /image endpoint
 * 
 * This servlet handles the upload and fetch of images stored int he DB as byte arrays 
 * 
 * @author matt @ Software Secured
 *
 */

@WebServlet("/image")
public class ImageManager extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final long MAX_IMAGE_SIZE_BYTES = 1024 * 1024 * 2; // 2MB
	private static final List<String> ACCEPTED_TYPES = Arrays.asList("image/jpeg","image/jpg","image/png");
	private static Logger log = LoggerFactory.getLogger(ImageManager.class);
	private DiskFileItemFactory factory;
       
	
	/**
	 * Call parent constructor
	 */
    public ImageManager() {
        super();
    }
    
    
    /**
     * Handle GET requests. This means serving up images from the DB.
     * Accepts the following parameters:
     *   id -> the id of the Image object generated by Hibernate
     */
    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	if(!ValidationSnipets.isValidSession(req.getSession())){
			res.sendRedirect("register");
			return;
		}
    	
    	log.info("Received GET request");
    	
    	
    	if(factory==null) {
			buildImageFactory();
		}
    	
    	String id_string = req.getParameter("id");
    	
    	if(id_string == null || id_string.equals("default") || id_string.equals("")) {
    		log.error("No such image. Sending dummy");
    		req.getRequestDispatcher("resources/img/dummy.png").forward(req, res);
    		return;
    	}
    	
    	Session sess = HibernateUtility.getSessionFactory().openSession();
		
    	
    	
    	try {
    		long id = Long.parseLong(id_string);
    		
    		Image img = (Image) sess.createQuery("FROM Image i WHERE i.id=:id").setParameter("id", id).list().get(0);
    		res.setContentLength(img.getSize());
    		res.setContentType(img.getContentType());
    		res.getOutputStream().write(img.getContent());
    		
    		log.info("Served image with ID {}",id);
    		
    	}catch(NumberFormatException e) {
    		log.error("{}",ValidationSnipets.sanitizeCRLF(e.getMessage()));
    	}catch(IndexOutOfBoundsException e) {
    		log.error("No such image. Sending dummy");
    		req.getRequestDispatcher("resources/img/dummy.png").forward(req, res);
    	}
    	
    	sess.close();
	}

    
    /**
     * Handle a POST request (Upload a new picture)
     * Uses the following parameters:
     *   owner_id -> the user_id of the account that will own the image (i.e. be able to delete it)
     *   creator_id -> the user_id of the account attempting to upload the image
     *   context -> the under which context the image is being uploaded (as profile pic or wall post)
     *   file -> (form-data) the image to be uploaded
     *   
     * Currently, this image will simply be set as the user's profile picture
     */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		if(!ValidationSnipets.isValidSession(req.getSession())){
			res.sendRedirect("register");
			return;
		}
		
		log.info("Received POST request");
		log.info("isMultipart: {}",ServletFileUpload.isMultipartContent(req));
		
		
		
		if(factory==null) {
			buildImageFactory();
		}
		
		String owner_id_string = "";
		String creator_id_string = "";
		String context = "";
		String label = "";
		String mimetype = "";
		
		Session sess = HibernateUtility.getSessionFactory().openSession();
		
		// Using Apache Commons File Upload handler
		try {
			//List<FileItem> items = new ServletFileUpload(factory).parseRequest(req);
			
			ServletFileUpload upload = new ServletFileUpload(factory);
			//upload.setFileSizeMax(MAX_IMAGE_SIZE_BYTES);
			List<FileItem> items = upload.parseRequest(req);
			
			log.info("isMultipart: {}",ServletFileUpload.isMultipartContent(req));
			log.info("Req CSRF_TOKENVALUE: {}",req.getParameter("OWASP-CSRFTOKEN"));
			log.info("Req CREATOR ID: {}",req.getParameter("creator_id"));
			log.info("Req OWNER ID: {}",req.getParameter("owner_id"));
			log.info("Req FILE: {}",req.getParameter("file"));

			int size = -1;
			byte[] data = null;
			
			// Parse the request parameters and handle retrieving the file
			for(FileItem i : items) {
				log.info("HERE");
				String name = i.getFieldName();
				String val = i.getString();
				
				switch (name) {
					case "owner_id":
						owner_id_string = val;
						break;
						
					case "creator_id":
						creator_id_string = val;
						break;
						
					case "label":
						label = val;
						break;
						
					case "context":
						context = val;
						break;
	
					default:
						break;
				}
				
				if(i.isFormField()) {
					log.info("Field: {}    Val: {}",ValidationSnipets.sanitizeCRLF(name),ValidationSnipets.sanitizeCRLF(val));
				}else {
					log.info("Size: {}",i.getSize());
					log.info("Field Name: {}",i.getFieldName());
					log.info("File Name: ",i.getName());
					
					if(i.getSize() < 1) {
						throw new FacebrokeException("No image");
					}
					if(i.getSize() > MAX_IMAGE_SIZE_BYTES) {
						throw new FacebrokeException("Image is too large. Must be less than 2MB");
					}
					
					// Retrieve uploaded content as byte[]
					data = i.get();
					size = (int) i.getSize();
					
					// Try to validate as an image
					ImageInfo info = Imaging.getImageInfo(data);
					mimetype = info.getMimeType();
					
					if(!ACCEPTED_TYPES.contains(mimetype)) {
						throw new FacebrokeException("Image must be of type png or jpeg/jpg");
					}
					
				}
			}
			
			if(data==null) {
				// HANDLE BAD REQUEST
			}
			
			User owner, creator;
			
			owner = (User)sess.createQuery("From User u WHERE u.id=:user_id")
								.setParameter("user_id", Long.parseLong(owner_id_string))
								.list()
								.get(0);
			
			creator = (User)sess.createQuery("From User u WHERE u.id=:user_id")
					.setParameter("user_id", Long.parseLong(creator_id_string))
					.list()
					.get(0);
			
			
			// Fix GitHub issue 3 - IDOR
			if(creator.getId() != (long)req.getSession().getAttribute("user_id")) {
				throw new FacebrokeException("Can't create a post as another user....");
			}
			
			sess.beginTransaction();
			Image img = new Image(owner, creator, Image.Viewable.All, data, size, label, mimetype);
			sess.save(img);
			
			// Updating own profile picture
			if(context.equals("profile") && creator.equals(owner)) {
				Image current = owner.getProfilePicture();
				owner.setProfilePicture(img);
				sess.save(owner);
				req.getSession().setAttribute("user_pic_id", img.getId());
				if (current != null) {
					// Delete the User's previous picture
					log.info("Trying to delete image {}",current.getId());
					sess.delete(current);
				}
			}
			// Trying to update another user
			else if(context.equals("profile")) {
				if(!creator.getRole().equals(UserRole.ADMIN)) {
					// Not an admin, not allowed
					throw new FacebrokeException("Don't have permission to modify other's settings");
				}
				
				Image current = owner.getProfilePicture();
				owner.setProfilePicture(img);
				sess.save(owner);
				if (current != null) {
					// Delete the User's previous picture
					log.info("Trying to delete image {}",current.getId());
					sess.delete(current);
				}
			}else {
				//TODO regular image upload
			}
			
			sess.getTransaction().commit();
			log.info("Created new img: {}",ValidationSnipets.sanitizeCRLF(img.toString()));
			log.info("Mimetype: "+mimetype);
			
			if(context.equals("profile")) {
				res.sendRedirect("settings?id="+owner.getId());
			}
			
		}catch(FileUploadException e) {
			req.setAttribute("serverMessage", e.getMessage());
			req.getRequestDispatcher("error.jsp").forward(req, res);
			sess.close();
			return;
		}catch(NumberFormatException e) {
			log.error("Parsing: {}",ValidationSnipets.sanitizeCRLF(e.getMessage()));
			sess.close();
		}catch(FacebrokeException e) {
			req.setAttribute("serverMessage", e.getMessage());
			req.getRequestDispatcher("error.jsp").forward(req, res);
			sess.close();
			return;
		} catch (ImageReadException e) {
			req.setAttribute("serverMessage", e.getMessage());
			req.getRequestDispatcher("error.jsp").forward(req, res);
			sess.close();
			return;
		}
		
		
		log.info("Received a file of type: "+mimetype);
		sess.close();
	}



	/**
	 * Create a temp-file factory for Apache Commons File Upload to use
	 */
	private void buildImageFactory() {
		factory = new DiskFileItemFactory();
		
		ServletContext ctx = this.getServletConfig().getServletContext();
		File repo = (File) ctx.getAttribute("javax.servlet.context.tempdir");
		factory.setRepository(repo);
	}
}
