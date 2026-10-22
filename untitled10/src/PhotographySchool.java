import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class PhotographySchool {
    private static final int PORT = 8080;
    private static final List<User> users = new ArrayList<>();
    private static final List<Course> courses = new ArrayList<>();
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    private static final Map<Long, List<LearningStage>> courseStages = new HashMap<>();
    private static final String PROMO_CODE = "1111";

    public static void main(String[] args) {
        initializeSampleData();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=========================================");
            System.out.println("Сайт обучения 'Стань фотографом' запущен!");
            System.out.println("Перейдите по адресу: http://localhost:" + PORT);
            System.out.println("=========================================");

            ExecutorService executor = Executors.newFixedThreadPool(10);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== МОДЕЛИ ДАННЫХ ====================

    static class User {
        private Long id;
        private String email;
        private String password;
        private String fullName;
        private String role;
        private LocalDateTime registrationDate;
        private boolean hasPromoDiscount;

        public User(Long id, String email, String password, String fullName, String role, boolean hasPromoDiscount) {
            this.id = id;
            this.email = email;
            this.password = password;
            this.fullName = fullName;
            this.role = role;
            this.hasPromoDiscount = hasPromoDiscount;
            this.registrationDate = LocalDateTime.now();
        }

        // Геттеры
        public Long getId() { return id; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
        public LocalDateTime getRegistrationDate() { return registrationDate; }
        public boolean hasPromoDiscount() { return hasPromoDiscount; }
        public void setHasPromoDiscount(boolean hasPromoDiscount) { this.hasPromoDiscount = hasPromoDiscount; }
    }

    static class Course {
        private Long id;
        private String title;
        private String description;
        private String level;
        private double originalPrice;
        private int studentCount;
        private String imageUrl;
        private String instructor;
        private int durationWeeks;

        public Course(Long id, String title, String description, String level, double originalPrice,
                      String imageUrl, String instructor, int durationWeeks) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.level = level;
            this.originalPrice = originalPrice;
            this.studentCount = new Random().nextInt(500) + 100;
            this.imageUrl = imageUrl;
            this.instructor = instructor;
            this.durationWeeks = durationWeeks;
        }

        // Геттеры
        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getLevel() { return level; }
        public double getOriginalPrice() { return originalPrice; }
        public int getStudentCount() { return studentCount; }
        public String getImageUrl() { return imageUrl; }
        public String getInstructor() { return instructor; }
        public int getDurationWeeks() { return durationWeeks; }

        public String getPriceFormatted(boolean hasDiscount) {
            double price = hasDiscount ? 0 : originalPrice;
            return price == 0 ? "Бесплатно" : String.format("%,.0f руб.", price);
        }

        public double getPrice(boolean hasDiscount) {
            return hasDiscount ? 0 : originalPrice;
        }
    }

    static class LearningStage {
        private int number;
        private String title;
        private String description;
        private String content;
        private String icon;

        public LearningStage(int number, String title, String description, String content, String icon) {
            this.number = number;
            this.title = title;
            this.description = description;
            this.content = content;
            this.icon = icon;
        }

        // Геттеры
        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getContent() { return content; }
        public String getIcon() { return icon; }
    }

    // ==================== ОБРАБОТЧИК КЛИЕНТА ====================

    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true)
        ) {
            String requestLine = in.readLine();
            if (requestLine == null) return;

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) return;

            String method = requestParts[0];
            String path = requestParts[1];

            // Читаем заголовки
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String[] headerParts = line.split(": ", 2);
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // Читаем тело запроса для POST
            StringBuilder body = new StringBuilder();
            if (headers.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(headers.get("Content-Length"));
                char[] buffer = new char[contentLength];
                int read = in.read(buffer, 0, contentLength);
                if (read > 0) {
                    body.append(buffer, 0, read);
                }
            }

            // Обрабатываем запрос
            String response = handleRequest(method, path, headers, body.toString());
            out.print(response);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== ОБРАБОТКА ЗАПРОСОВ ====================

    private static String handleRequest(String method, String path,
                                        Map<String, String> headers,
                                        String body) {

        // Главная страница
        if (path.equals("/") || path.equals("/index.html")) {
            return getHomePage(headers);
        }

        // Страница курсов
        if (path.equals("/courses.html")) {
            return getCoursesPage(headers);
        }

        // Страница регистрации
        if (path.equals("/register.html")) {
            return getRegisterPage();
        }

        // Обработка регистрации
        if (path.equals("/register") && method.equals("POST")) {
            return handleRegistration(body);
        }

        // Страница входа
        if (path.equals("/login.html")) {
            return getLoginPage();
        }

        // Обработка входа
        if (path.equals("/login") && method.equals("POST")) {
            return handleLogin(body);
        }

        // Профиль
        if (path.equals("/profile.html")) {
            return getProfilePage(headers);
        }

        // Выход
        if (path.equals("/logout")) {
            return handleLogout(headers);
        }

        // Страница курса с этапами обучения
        if (path.startsWith("/course/")) {
            String[] parts = path.split("/");
            if (parts.length == 3) {
                try {
                    Long courseId = Long.parseLong(parts[2]);
                    return getCoursePage(courseId, headers);
                } catch (NumberFormatException e) {
                    return getNotFoundPage();
                }
            }
            return getNotFoundPage();
        }

        // Этап обучения
        if (path.startsWith("/stage/")) {
            String[] pathParts = path.split("/");
            if (pathParts.length == 4) {
                try {
                    Long courseId = Long.parseLong(pathParts[2]);
                    int stageNumber = Integer.parseInt(pathParts[3]);
                    return getStagePage(courseId, stageNumber);
                } catch (NumberFormatException e) {
                    return getNotFoundPage();
                }
            }
            return getNotFoundPage();
        }

        // Запись на курс
        if (path.startsWith("/enroll/") && method.equals("POST")) {
            try {
                Long courseId = Long.parseLong(path.substring(8));
                return handleEnrollment(courseId, headers, body);
            } catch (NumberFormatException e) {
                return getNotFoundPage();
            }
        }

        // API для курсов
        if (path.equals("/api/courses")) {
            String sessionId = getSessionId(headers);
            boolean hasDiscount = getUserFromSession(sessionId) != null && getUserFromSession(sessionId).hasPromoDiscount();
            return getCoursesJson(hasDiscount);
        }

        // Favicon
        if (path.equals("/favicon.ico")) {
            return getFavicon();
        }

        // Страница 404
        return getNotFoundPage();
    }

    // ==================== HTML СТРАНИЦЫ ====================

    private static String getHomePage(Map<String, String> headers) {
        String sessionId = getSessionId(headers);
        User user = getUserFromSession(sessionId);
        boolean hasDiscount = user != null && user.hasPromoDiscount();

        StringBuilder html = new StringBuilder();
        html.append("HTTP/1.1 200 OK\r\n");
        html.append("Content-Type: text/html; charset=UTF-8\r\n");
        html.append("\r\n");

        html.append("<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Стань фотографом</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Arial', sans-serif; line-height: 1.6; color: #333; }\n" +
                "        .header { \n" +
                "            background: rgba(0, 0, 0, 0.7); /* Прозрачный черный фон */\n" +
                "            color: white; \n" +
                "            padding: 1rem 0; \n" +
                "            position: fixed; \n" +
                "            width: 100%; \n" +
                "            top: 0; \n" +
                "            z-index: 1000; \n" +
                "            backdrop-filter: blur(10px); /* Эффект размытия фона */\n" +
                "            border-bottom: 1px solid rgba(255, 255, 255, 0.1);\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .header.scrolled {\n" +
                "            background: rgba(0, 0, 0, 0.9);\n" +
                "            padding: 0.7rem 0;\n" +
                "        }\n" +
                "        .nav { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; padding: 0 2rem; }\n" +
                "        .logo-container { \n" +
                "            display: flex; \n" +
                "            align-items: center; \n" +
                "            gap: 0.5rem; \n" +
                "            height: 60px;\n" +
                "        }\n" +
                "        .logo-img { \n" +
                "            height: 50px; \n" +
                "            width: auto; \n" +
                "            object-fit: contain;\n" +
                "            filter: drop-shadow(0 2px 5px rgba(0,0,0,0.5));\n" +
                "        }\n" +
                "        .nav-links { display: flex; gap: 2rem; align-items: center; }\n" +
                "        .nav-links a { \n" +
                "            color: white; \n" +
                "            text-decoration: none; \n" +
                "            font-weight: 500; \n" +
                "            padding: 0.5rem 1rem;\n" +
                "            border-radius: 5px;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .nav-links a:hover { background: rgba(255, 255, 255, 0.1); }\n" +
                "        .hero { \n" +
                "            padding: 8rem 2rem 5rem 2rem; /* Увеличен верхний padding из-за фиксированного хедера */\n" +
                "            text-align: center; \n" +
                "            background: linear-gradient(rgba(0, 0, 0, 0.7), rgba(0, 0, 0, 0.4)), \n" +
                "                      url('https://a-static.besthdwallpaper.com/laptop-and-camera-wallpaper-1920x720-12422_92.jpg'); \n" +
                "            background-size: cover; \n" +
                "            background-position: center; \n" +
                "            background-repeat: no-repeat; \n" +
                "            background-attachment: fixed;\n" +
                "            color: white; \n" +
                "            min-height: 100vh;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        .hero h1 { \n" +
                "            font-size: 3.5rem; \n" +
                "            margin-bottom: 1.5rem; \n" +
                "            color: white; \n" +
                "            text-shadow: 2px 2px 10px rgba(0,0,0,0.8);\n" +
                "            max-width: 900px;\n" +
                "        }\n" +
                "        .hero p { \n" +
                "            font-size: 1.4rem; \n" +
                "            color: white; \n" +
                "            max-width: 700px; \n" +
                "            margin: 0 auto 2.5rem; \n" +
                "            text-shadow: 1px 1px 5px rgba(0,0,0,0.7);\n" +
                "            background-color: rgba(0, 0, 0, 0.5);\n" +
                "            padding: 1.5rem;\n" +
                "            border-radius: 15px;\n" +
                "            backdrop-filter: blur(5px);\n" +
                "            border: 1px solid rgba(255, 255, 255, 0.1);\n" +
                "        }\n" +
                "        .btn { \n" +
                "            display: inline-block; \n" +
                "            padding: 1.2rem 2.5rem; \n" +
                "            background: linear-gradient(45deg, #e74c3c, #f39c12);\n" +
                "            color: white; \n" +
                "            text-decoration: none; \n" +
                "            border-radius: 50px; \n" +
                "            font-weight: bold; \n" +
                "            font-size: 1.2rem;\n" +
                "            border: 2px solid rgba(255, 255, 255, 0.3);\n" +
                "            transition: all 0.3s ease;\n" +
                "            box-shadow: 0 8px 20px rgba(0,0,0,0.4);\n" +
                "        }\n" +
                "        .btn:hover {\n" +
                "            transform: translateY(-5px);\n" +
                "            box-shadow: 0 12px 25px rgba(0,0,0,0.6);\n" +
                "            background: linear-gradient(45deg, #c0392b, #e67e22);\n" +
                "        }\n" +
                "        .container { max-width: 1200px; margin: 0 auto; padding: 3rem 2rem; }\n" +
                "        .course-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; margin-top: 3rem; }\n" +
                "        .course-card { background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1); transition: transform 0.3s ease; }\n" +
                "        .course-card:hover { transform: translateY(-10px); }\n" +
                "        .course-image { height: 200px; background-size: cover; background-position: center; }\n" +
                "        .course-content { padding: 1.5rem; }\n" +
                "        .course-title { font-size: 1.3rem; margin-bottom: 0.5rem; color: #2c3e50; }\n" +
                "        .course-description { color: #666; margin-bottom: 1rem; }\n" +
                "        .course-meta { display: flex; justify-content: space-between; color: #888; font-size: 0.9rem; margin-bottom: 1rem; }\n" +
                "        .course-price { font-size: 1.5rem; font-weight: bold; color: #e74c3c; }\n" +
                "        .course-price.free { color: #2ecc71; }\n" +
                "        .footer { background: #2c3e50; color: white; text-align: center; padding: 3rem 2rem; margin-top: 3rem; }\n" +
                "        .features { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 2rem; margin: 3rem 0; }\n" +
                "        .feature { text-align: center; padding: 2rem; background: rgba(255, 255, 255, 0.1); border-radius: 10px; }\n" +
                "        .feature-icon { font-size: 3rem; margin-bottom: 1rem; color: #3498db; }\n" +
                "        .user-info { \n" +
                "            color: white; \n" +
                "            display: flex; \n" +
                "            align-items: center; \n" +
                "            gap: 1rem;\n" +
                "            background: rgba(255, 255, 255, 0.1);\n" +
                "            padding: 0.5rem 1rem;\n" +
                "            border-radius: 5px;\n" +
                "        }\n" +
                "        .user-info a { color: #3498db; }\n" +
                "        .user-info a:hover { color: #2980b9; }\n" +
                "    </style>\n" +
                "    <script>\n" +
                "        window.addEventListener('scroll', function() {\n" +
                "            const header = document.querySelector('.header');\n" +
                "            if (window.scrollY > 50) {\n" +
                "                header.classList.add('scrolled');\n" +
                "            } else {\n" +
                "                header.classList.remove('scrolled');\n" +
                "            }\n" +
                "        });\n" +
                "    </script>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <nav class=\"nav\">\n" +
                "            <div class=\"nav-links\">\n" +
                "                <a href=\"/\">Главная</a>\n" +
                "                <a href=\"/courses.html\">Курсы</a>\n");

        if (user == null) {
            html.append("                <a href=\"/register.html\">Регистрация</a>\n" +
                    "                <a href=\"/login.html\">Вход</a>\n");
        } else {
            html.append("                <div class=\"user-info\">\n" +
                    "                    <span>👤 " + user.getFullName() + "</span>\n" +
                    "                    <a href=\"/profile.html\">Профиль</a>\n" +
                    "                    <a href=\"/logout\">Выход</a>\n" +
                    "                </div>\n");
        }





        html.append("            </div>\n" +
                "        </nav>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"hero\">\n" +
                "        <h1>Научись фотографировать как профессионал</h1>\n" +
                "        <p>Пройдите онлайн-курсы от лучших фотографов.</p>\n" +
                "        <a href=\"/courses.html\" class=\"btn\">Выбрать курс</a>\n" +
                "    </div>\n" );


        html.append("        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"footer\">\n" +
                "        <div class=\"container\">\n" +
                "            <h3> Стань фотографом</h3>\n" +
                "            <p style=\"margin: 1rem 0;\">© 2025 Онлайн-школа фотографии. Все права защищены.</p>\n" +
                "            <p>Контакты: info@photography-school.ru | +7 (999) 123-45-67</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>");

        return html.toString();
    }

    private static String getCoursesPage(Map<String, String> headers) {
        String sessionId = getSessionId(headers);
        User user = getUserFromSession(sessionId);
        boolean hasDiscount = user != null && user.hasPromoDiscount();

        StringBuilder html = new StringBuilder();
        html.append("HTTP/1.1 200 OK\r\n");
        html.append("Content-Type: text/html; charset=UTF-8\r\n");
        html.append("\r\n");

        html.append("<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Все курсы фотографии</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Arial', sans-serif; background: #f8f9fa; }\n" +
                "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 1rem 0; }\n" +
                "        .nav { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; padding: 0 2rem; }\n" +
                "        .logo { font-size: 1.5rem; font-weight: bold; display: flex; align-items: center; gap: 0.5rem; }\n" +
                "        .nav-links { display: flex; gap: 2rem; align-items: center; }\n" +
                "        .nav-links a { color: white; text-decoration: none; font-weight: 500; }\n" +
                "        .container { max-width: 1200px; margin: 0 auto; padding: 2rem; }\n" +
                "        .course-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 2rem; }\n" +
                "        .course-card { background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                "        .course-image { height: 200px; background-size: cover; background-position: center; position: relative; }\n" +
                "        .course-level { position: absolute; top: 1rem; right: 1rem; padding: 0.25rem 0.75rem; border-radius: 20px; font-size: 0.8rem; color: white; }\n" +
                "        .level-beginner { background: #2ecc71; }\n" +
                "        .level-intermediate { background: #f39c12; }\n" +
                "        .level-advanced { background: #e74c3c; }\n" +
                "        .course-content { padding: 1.5rem; }\n" +
                "        .course-title { font-size: 1.3rem; margin-bottom: 0.5rem; color: #2c3e50; }\n" +
                "        .course-instructor { color: #666; margin-bottom: 1rem; font-size: 0.9rem; }\n" +
                "        .course-description { color: #666; margin-bottom: 1rem; }\n" +
                "        .course-meta { display: flex; justify-content: space-between; color: #888; font-size: 0.9rem; margin-bottom: 1rem; }\n" +
                "        .course-price { font-size: 1.5rem; font-weight: bold; text-align: right; }\n" +
                "        .price-paid { color: #e74c3c; }\n" +
                "        .price-free { color: #2ecc71; }\n" +
                "        .btn { display: inline-block; padding: 0.75rem 1.5rem; background: #3498db; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; width: 100%; text-align: center; margin-top: 1rem; }\n" +
                "        .user-info { color: white; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <nav class=\"nav\">\n" +
                "            <div class=\"logo\">Все курсы</div>\n" +
                "            <div class=\"nav-links\">\n" +
                "                <a href=\"/\">Главная</a>\n" +
                "                <a href=\"/courses.html\">Курсы</a>\n");

        if (user == null) {
            html.append("                <a href=\"/register.html\">Регистрация</a>\n" +
                    "                <a href=\"/login.html\">Вход</a>\n");
        } else {
            html.append("                <div class=\"user-info\">\n" +
                    "                    " + user.getFullName() + "\n" +
                    "                    <a href=\"/profile.html\" style=\"margin-left: 1rem;\">Профиль</a>\n" +
                    "                    <a href=\"/logout\" style=\"margin-left: 1rem;\">Выход</a>\n" +
                    "                </div>\n");
        }

        html.append("            </div>\n" +
                "        </nav>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"container\">\n" +
                "        <h1 style=\"margin-bottom: 0.5rem;\">Все курсы по фотографии</h1>\n" +
                "        \n" +
                "        <div class=\"course-grid\">\n");

        // Добавляем все курсы
        for (Course course : courses) {
            String levelClass = "";
            switch (course.getLevel().toLowerCase()) {
                case "начинающий": levelClass = "level-beginner"; break;
                case "средний": levelClass = "level-intermediate"; break;
                case "продвинутый": levelClass = "level-advanced"; break;
            }

            String priceClass = hasDiscount ? "price-free" : "price-paid";
            String priceText = course.getPriceFormatted(hasDiscount);

            html.append("            <div class=\"course-card\">\n" +
                    "                <div class=\"course-image\" style=\"background-image: url('" + course.getImageUrl() + "');\">\n" +
                    "                    <span class=\"course-level " + levelClass + "\">" + course.getLevel() + "</span>\n" +
                    "                </div>\n" +
                    "                <div class=\"course-content\">\n" +
                    "                    <h3 class=\"course-title\">" + course.getTitle() + "</h3>\n" +
                    "                    <p class=\"course-instructor\">Преподаватель: " + course.getInstructor() + "</p>\n" +
                    "                    <p class=\"course-description\">" + course.getDescription() + "</p>\n" +
                    "                    <div class=\"course-price " + priceClass + "\">" + priceText + "</div>\n" +
                    "                    <a href=\"/course/" + course.getId() + "\" class=\"btn\">Подробнее о курсе</a>\n" +
                    "                </div>\n" +
                    "            </div>\n");
        }

        html.append("        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>");

        return html.toString();
    }

    private static String getCoursePage(Long courseId, Map<String, String> headers) {
        Course course = findCourseById(courseId);
        if (course == null) {
            return getNotFoundPage();
        }

        String sessionId = getSessionId(headers);
        User user = getUserFromSession(sessionId);
        boolean hasDiscount = user != null && user.hasPromoDiscount();

        List<LearningStage> stages = courseStages.getOrDefault(courseId, new ArrayList<>());
        String stagesJson = getStagesJson(courseId);

        StringBuilder html = new StringBuilder();
        html.append("HTTP/1.1 200 OK\r\n");
        html.append("Content-Type: text/html; charset=UTF-8\r\n");
        html.append("\r\n");

        html.append("<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>" + course.getTitle() + "</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Arial', sans-serif; background: #f8f9fa; }\n" +
                "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 1rem 0; }\n" +
                "        .nav { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; padding: 0 2rem; }\n" +
                "        .nav-links { display: flex; gap: 2rem; align-items: center; }\n" +
                "        .nav-links a { color: white; text-decoration: none; font-weight: 500; }\n" +
                "        .course-header { background: white; padding: 3rem 2rem; text-align: center; }\n" +
                "        .course-title { font-size: 2.5rem; color: #2c3e50; margin-bottom: 1rem; }\n" +
                "        .course-image { max-width: 800px; margin: 2rem auto; border-radius: 10px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }\n" +
                "        .course-image img { width: 100%; height: 400px; object-fit: cover; }\n" +
                "        .container { max-width: 1200px; margin: 0 auto; padding: 2rem; }\n" +
                "        .course-info { display: grid; grid-template-columns: 2fr 1fr; gap: 3rem; margin: 3rem 0; }\n" +
                "        .course-description { background: white; padding: 2rem; border-radius: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                "        .course-sidebar { background: white; padding: 2rem; border-radius: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                "        .info-item { margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid #eee; }\n" +
                "        .info-label { color: #666; font-size: 0.9rem; }\n" +
                "        .info-value { font-weight: bold; color: #2c3e50; }\n" +
                "        .stages-section { margin: 3rem 0; }\n" +
                "        .stages-title { font-size: 2rem; text-align: center; margin-bottom: 2rem; color: #2c3e50; }\n" +
                "        .stages-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1.5rem; }\n" +
                "        .stage-card { background: white; border-radius: 10px; padding: 1.5rem; text-align: center; cursor: pointer; transition: transform 0.3s, box-shadow 0.3s; box-shadow: 0 3px 10px rgba(0,0,0,0.1); }\n" +
                "        .stage-card:hover { transform: translateY(-5px); box-shadow: 0 10px 20px rgba(0,0,0,0.15); }\n" +
                "        .stage-number { font-size: 2rem; font-weight: bold; color: #3498db; margin-bottom: 0.5rem; }\n" +
                "        .stage-icon { font-size: 2.5rem; margin-bottom: 1rem; }\n" +
                "        .stage-title { font-size: 1.1rem; font-weight: bold; margin-bottom: 0.5rem; color: #2c3e50; }\n" +
                "        .stage-description { color: #666; font-size: 0.9rem; }\n" +
                "        .btn { display: inline-block; padding: 1rem 2rem; background: #2ecc71; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; border: none; cursor: pointer; transition: background 0.3s; font-size: 1.1rem; }\n" +
                "        .btn:hover { background: #27ae60; }\n" +
                "        .btn-large { width: 100%; margin-top: 1rem; }\n" +
                "        .btn-enroll { background: #2ecc71; }\n" +
                "        .btn-login { background: #3498db; }\n" +
                "        .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000; }\n" +
                "        .modal-content { background: white; margin: 5% auto; padding: 2rem; border-radius: 10px; max-width: 800px; max-height: 80%; overflow-y: auto; }\n" +
                "        .close-modal { float: right; font-size: 2rem; cursor: pointer; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <nav class=\"nav\">\n" +
                "            <div class=\"logo\" style=\"font-size: 1.5rem; font-weight: bold;\"> Курс</div>\n" +
                "            <div class=\"nav-links\">\n" +
                "                <a href=\"/\">Главная</a>\n" +
                "                <a href=\"/courses.html\">Все курсы</a>\n");

        if (user == null) {
            html.append("                <a href=\"/login.html\">Войти</a>\n");
        } else {
            html.append("                <span style=\"color: white;\">Привет, " + user.getFullName() + "!</span>\n" +
                    "                <a href=\"/profile.html\">Профиль</a>\n" +
                    "                <a href=\"/logout\">Выход</a>\n");
        }

        html.append("            </div>\n" +
                "        </nav>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"course-header\">\n" +
                "        <h1 class=\"course-title\">" + course.getTitle() + "</h1>\n" +
                "        <p style=\"color: #666; max-width: 600px; margin: 0 auto; font-size: 1.1rem;\">" + course.getDescription() + "</p>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"course-image\">\n" +
                "        <img src=\"" + course.getImageUrl() + "\" alt=\"" + course.getTitle() + "\">\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"course-info\">\n" +
                "            <div class=\"course-description\">\n" +
                "                <h2 style=\"margin-bottom: 1rem; color: #2c3e50;\">О курсе</h2>\n" +
                "                <p style=\"line-height: 1.8; color: #555;\">" + course.getDescription() + "</p>\n" +
                "                \n" +
                "                <div class=\"stages-section\">\n" +
                "                    <h2 class=\"stages-title\">Программа курса</h2>\n" +
                "                    <div class=\"stages-grid\">\n");

        // Добавляем этапы обучения
        for (LearningStage stage : stages) {
            html.append("                        <div class=\"stage-card\" onclick=\"showStage(" + courseId + ", " + stage.getNumber() + ")\">\n" +
                    "                            <div class=\"stage-icon\">" + stage.getIcon() + "</div>\n" +
                    "                            <div class=\"stage-number\">" + String.format("%02d", stage.getNumber()) + "</div>\n" +
                    "                            <h3 class=\"stage-title\">" + stage.getTitle() + "</h3>\n" +
                    "                            <p class=\"stage-description\">" + stage.getDescription() + "</p>\n" +
                    "                        </div>\n");
        }

        html.append("                    </div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"course-sidebar\">\n" +
                "                <h2 style=\"margin-bottom: 1.5rem; color: #2c3e50;\">Информация о курсе</h2>\n" +
                "                \n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Преподаватель</div>\n" +
                "                    <div class=\"info-value\">" + course.getInstructor() + "</div>\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Уровень</div>\n" +
                "                    <div class=\"info-value\">" + course.getLevel() + "</div>\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Длительность</div>\n" +
                "                    <div class=\"info-value\">" + course.getDurationWeeks() + " недель</div>\n" +
                "                </div>\n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Стоимость</div>\n" +
                "                    <div class=\"info-value\" style=\"font-size: 2rem; " +
                (hasDiscount ? "color: #2ecc71;" : "color: #e74c3c;") + "\">" +
                course.getPriceFormatted(hasDiscount) + "</div>\n" +
                "                </div>\n" +
                "                \n" +
                "                <form action=\"/enroll/" + courseId + "\" method=\"post\">\n");

        if (user == null) {
            html.append("                    <a href=\"/login.html\" class=\"btn btn-large btn-login\">Войдите, чтобы записаться</a>\n");
        } else {
            html.append("                    <button type=\"submit\" class=\"btn btn-large btn-enroll\">Записаться на курс</button>\n");
        }

        html.append("                </form>\n" +
                "                \n" +
                "                <p style=\"margin-top: 2rem; color: #666; font-size: 0.9rem; line-height: 1.6;\">\n" +
                "                    💡 После записи вы получите доступ ко всем материалам курса, \n" +
                "                    сможете общаться с преподавателем и другими студентами, \n" +
                "                    выполнять практические задания и получать обратную связь.\n" +
                "                </p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <!-- Модальное окно для этапов -->\n" +
                "    <div id=\"stageModal\" class=\"modal\">\n" +
                "        <div class=\"modal-content\">\n" +
                "            <span class=\"close-modal\" onclick=\"closeModal()\">&times;</span>\n" +
                "            <div id=\"modalContent\"></div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script>\n" +
                "        const stages = " + stagesJson + ";\n" +
                "        \n" +
                "        function showStage(courseId, stageNumber) {\n" +
                "            const stage = stages[stageNumber - 1];\n" +
                "            const modal = document.getElementById('stageModal');\n" +
                "            const content = document.getElementById('modalContent');\n" +
                "            \n" +
                "            let html = '<h2 style=\"color: #2c3e50; margin-bottom: 1rem;\">Этап ' + stageNumber + ': ' + stage.title + '</h2>';\n" +
                "            html += '<div style=\"background: #f8f9fa; padding: 1.5rem; border-radius: 8px; margin: 1.5rem 0;\">';\n" +
                "            html += '<p style=\"color: #666; font-style: italic;\">' + stage.description + '</p>';\n" +
                "            html += '</div>';\n" +
                "            html += '<div style=\"line-height: 1.8; color: #555; font-size: 1.1rem;\">';\n" +
                "            html += stage.content;\n" +
                "            html += '</div>';\n" +
                "            \n" +
                "            content.innerHTML = html;\n" +
                "            modal.style.display = 'block';\n" +
                "        }\n" +
                "        \n" +
                "        function closeModal() {\n" +
                "            document.getElementById('stageModal').style.display = 'none';\n" +
                "        }\n" +
                "        \n" +
                "        // Закрытие модального окна при клике вне его\n" +
                "        window.onclick = function(event) {\n" +
                "            const modal = document.getElementById('stageModal');\n" +
                "            if (event.target == modal) {\n" +
                "                closeModal();\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>");

        return html.toString();
    }



    private static String getLoginPage() {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Вход в систему</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }\n" +
                "        .container { max-width: 400px; margin: 50px auto; background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }\n" +
                "        h1 { text-align: center; color: #2c3e50; margin-bottom: 1.5rem; }\n" +
                "        .form-group { margin-bottom: 1rem; }\n" +
                "        label { display: block; margin-bottom: 0.5rem; font-weight: bold; }\n" +
                "        input { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; }\n" +
                "        button { width: 100%; padding: 0.75rem; background: #2ecc71; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 1.1rem; font-weight: bold; }\n" +
                "        button:hover { background: #27ae60; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>Вход в систему</h1>\n" +
                "        <form action=\"/login\" method=\"post\">\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Email:</label>\n" +
                "                <input type=\"email\" name=\"email\" required>\n" +
                "            </div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Пароль:</label>\n" +
                "                <input type=\"password\" name=\"password\" required>\n" +
                "            </div>\n" +
                "            <button type=\"submit\">Войти</button>\n" +
                "        </form>\n" +
                "        <p style=\"margin-top: 1rem; text-align: center;\">\n" +
                "            Нет аккаунта? <a href=\"/register.html\">Зарегистрироваться</a>\n" +
                "        </p>\n" +
                "        <p style=\"text-align: center; margin-top: 0.5rem;\">\n" +
                "            <a href=\"/\">Вернуться на главную</a>\n" +
                "        </p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String getRegisterPage() {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Регистрация</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }\n" +
                "        .container { max-width: 400px; margin: 50px auto; background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }\n" +
                "        h1 { text-align: center; color: #2c3e50; margin-bottom: 1.5rem; }\n" +
                "        .form-group { margin-bottom: 1rem; }\n" +
                "        label { display: block; margin-bottom: 0.5rem; font-weight: bold; }\n" +
                "        input { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; }\n" +
                "        button { width: 100%; padding: 0.75rem; background: #3498db; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 1.1rem; font-weight: bold; }\n" +
                "        button:hover { background: #2980b9; }\n" +
                "        .promo-info { background: #f8f9fa; padding: 1rem; border-radius: 5px; margin: 1rem 0; border-left: 4px solid #2ecc71; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>Регистрация</h1>\n" +
                "        \n" +
                "        <div class=\"promo-info\">\n" +
                "            <strong>🎁 Специальное предложение!</strong>\n" +
                "            <p style=\"margin: 0.5rem 0 0 0; color: #666;\">Введите промокод при регистрации для получения скидки!</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <form action=\"/register\" method=\"post\">\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Имя и фамилия:</label>\n" +
                "                <input type=\"text\" name=\"fullName\" required>\n" +
                "            </div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Email:</label>\n" +
                "                <input type=\"email\" name=\"email\" required>\n" +
                "            </div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Пароль:</label>\n" +
                "                <input type=\"password\" name=\"password\" required>\n" +
                "            </div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Промокод (необязательно):</label>\n" +
                "                <input type=\"text\" name=\"promoCode\" placeholder=\"Введите промокод\">\n" +
                "            </div>\n" +
                "            <button type=\"submit\">Зарегистрироваться</button>\n" +
                "        </form>\n" +
                "        <p style=\"margin-top: 1rem; text-align: center;\">\n" +
                "            Уже есть аккаунт? <a href=\"/login.html\">Войти</a>\n" +
                "        </p>\n" +
                "        <p style=\"text-align: center; margin-top: 0.5rem;\">\n" +
                "            <a href=\"/\">Вернуться на главную</a>\n" +
                "        </p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }


    private static String getProfilePage(Map<String, String> headers) {
        String sessionId = getSessionId(headers);
        User user = getUserFromSession(sessionId);

        if (user == null) {
            return "HTTP/1.1 302 Found\r\nLocation: /login.html\r\n\r\n";
        }

        String registrationDate = user.getRegistrationDate() != null ?
                user.getRegistrationDate().toString().substring(0, 16) :
                "Неизвестно";

        String promoStatus = user.hasPromoDiscount() ?
                "Активирован <span style=\"color: #2ecc71; font-weight: bold;\">✓</span>" :
                "Не активирован";

        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Профиль пользователя</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Arial', sans-serif; background: #f5f5f5; }\n" +
                "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 1rem 0; }\n" +
                "        .nav { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; padding: 0 2rem; }\n" +
                "        .logo { font-size: 1.5rem; font-weight: bold; display: flex; align-items: center; gap: 0.5rem; }\n" +
                "        .nav-links { display: flex; gap: 2rem; align-items: center; }\n" +
                "        .nav-links a { color: white; text-decoration: none; font-weight: 500; }\n" +
                "        .container { max-width: 800px; margin: 2rem auto; padding: 0 2rem; }\n" +
                "        .profile-card { background: white; padding: 2rem; border-radius: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                "        .profile-header { text-align: center; margin-bottom: 2rem; }\n" +
                "        .profile-info { margin-bottom: 2rem; }\n" +
                "        .info-item { margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid #eee; }\n" +
                "        .info-label { color: #666; font-size: 0.9rem; }\n" +
                "        .info-value { font-weight: bold; color: #2c3e50; }\n" +
                "        .btn { display: inline-block; padding: 0.75rem 1.5rem; background: #3498db; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }\n" +
                "        .btn:hover { background: #2980b9; }\n" +
                "        .user-email { color: #666; font-size: 0.9rem; margin-top: 0.5rem; }\n" +
                "        .promo-active { color: #2ecc71; }\n" +
                "        .promo-inactive { color: #e74c3c; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <nav class=\"nav\">\n" +
                "            <div class=\"logo\"> Профиль</div>\n" +
                "            <div class=\"nav-links\">\n" +
                "                <a href=\"/\">Главная</a>\n" +
                "                <a href=\"/courses.html\">Курсы</a>\n" +
                "                <a href=\"/logout\">Выход</a>\n" +
                "            </div>\n" +
                "        </nav>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"profile-card\">\n" +
                "            <div class=\"profile-header\">\n" +
                "                <h1 style=\"color: #2c3e50;\">Добро пожаловать, " + user.getFullName() + "!</h1>\n" +
                "                <p class=\"user-email\">" + user.getEmail() + "</p>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"profile-info\">\n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Email</div>\n" +
                "                    <div class=\"info-value\">" + user.getEmail() + "</div>\n" +
                "                </div>\n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Дата регистрации</div>\n" +
                "                    <div class=\"info-value\">" + registrationDate + "</div>\n" +
                "                </div>\n" +
                "                <div class=\"info-item\">\n" +
                "                    <div class=\"info-label\">Статус промокода</div>\n" +
                "                    <div class=\"info-value\">" + promoStatus + "</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div style=\"text-align: center; margin: 2rem 0;\">\n" +
                "                <a href=\"/courses.html\" class=\"btn\">Выбрать новые курсы</a>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div style=\"text-align: center; margin-top: 2rem; color: #666; font-size: 0.9rem;\">\n" +
                "        <p>© 2025 \"Стань фотографом\". Все права защищены.</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String handleRegistration(String body) {
        Map<String, String> params = parseFormData(body);
        String email = params.get("email");
        String password = params.get("password");
        String fullName = params.get("fullName");
        String promoCode = params.get("promoCode");

        // Проверяем существование пользователя
        for (User user : users) {
            if (user.getEmail().equals(email)) {
                return "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "\r\n" +
                        "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "    <meta charset=\"UTF-8\">\n" +
                        "    <title>Ошибка регистрации</title>\n" +
                        "    <style>\n" +
                        "        body { font-family: Arial; padding: 2rem; text-align: center; }\n" +
                        "        .error { color: #e74c3c; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <div class=\"error\">\n" +
                        "        <h1>Ошибка: пользователь с email " + email + " уже существует</h1>\n" +
                        "        <p><a href=\"/register.html\">Вернуться к регистрации</a></p>\n" +
                        "    </div>\n" +
                        "</body>\n" +
                        "</html>";
            }
        }

        // Проверяем промокод
        boolean hasPromoDiscount = PROMO_CODE.equals(promoCode);

        // Создаем нового пользователя
        Long newId = users.isEmpty() ? 1L : users.get(users.size() - 1).getId() + 1;
        User newUser = new User(newId, email, password, fullName, "STUDENT", hasPromoDiscount);
        users.add(newUser);

        // Создаем сессию
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, email);

        String message = hasPromoDiscount ?
                "<h2>🎉 Промокод активирован! Все курсы теперь бесплатны!</h2>" :
                "<p>Промокод не активирован или введен неверно.</p>";

        return "HTTP/1.1 200 OK\r\n" +
                "Set-Cookie: sessionId=" + sessionId + "; Path=/\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Регистрация успешна</title>\n" +
                "    <meta http-equiv=\"refresh\" content=\"3;url=/\">\n" +
                "    <style>\n" +
                "        body { font-family: Arial; padding: 2rem; text-align: center; background: #f5f5f5; }\n" +
                "        .success { background: white; padding: 2rem; border-radius: 10px; max-width: 600px; margin: 2rem auto; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                "        h1 { color: #2ecc71; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"success\">\n" +
                "        <h1>✅ Регистрация успешна!</h1>\n" +
                "        " + message + "\n" +
                "        <p>Перенаправление на главную страницу через 3 секунды...</p>\n" +
                "        <p><a href=\"/\">Перейти сразу</a></p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String handleLogin(String body) {
        Map<String, String> params = parseFormData(body);
        String email = params.get("email");
        String password = params.get("password");

        // Ищем пользователя
        for (User user : users) {
            if (user.getEmail().equals(email) && user.getPassword().equals(password)) {
                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, email);

                return String.format("""
                    HTTP/1.1 200 OK\r
                    Set-Cookie: sessionId=%s; Path=/\r
                    Content-Type: text/html; charset=UTF-8\r
                    \r
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Вход выполнен</title>
                        <meta http-equiv="refresh" content="2;url=/">
                    </head>
                    <body style="font-family: Arial; padding: 2rem; text-align: center;">
                        <h1 style="color: #2ecc71;">✅ Вход выполнен успешно!</h1>
                        <p>Перенаправление на главную страницу...</p>
                    </body>
                    </html>
                    """, sessionId);
            }
        }

        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Ошибка входа</title>
                    <style>
                        body { font-family: Arial; padding: 2rem; text-align: center; }
                        .error { color: #e74c3c; }
                    </style>
                </head>
                <body>
                    <div class="error">
                        <h1>Ошибка: неверный email или пароль</h1>
                        <p><a href="/login.html">Попробовать снова</a></p>
                    </div>
                </body>
                </html>
                """;
    }

    private static String handleLogout(Map<String, String> headers) {
        String sessionId = getSessionId(headers);
        if (sessionId != null) {
            sessions.remove(sessionId);
        }

        return "HTTP/1.1 302 Found\r\n" +
                "Set-Cookie: sessionId=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT\r\n" +
                "Location: /\r\n" +
                "\r\n";
    }

    private static String handleEnrollment(Long courseId, Map<String, String> headers, String body) {
        String sessionId = getSessionId(headers);
        User user = getUserFromSession(sessionId);

        if (user == null) {
            return "HTTP/1.1 302 Found\r\nLocation: /login.html\r\n\r\n";
        }

        Course course = findCourseById(courseId);
        if (course != null) {
            return String.format("""
                HTTP/1.1 200 OK\r
                Content-Type: text/html; charset=UTF-8\r
                \r
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Запись на курс</title>
                    <style>
                        body { font-family: Arial; padding: 2rem; text-align: center; background: #f5f5f5; }
                        .success { background: white; padding: 2rem; border-radius: 10px; max-width: 600px; margin: 2rem auto; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
                        h1 { color: #2ecc71; }
                    </style>
                </head>
                <body>
                    <div class="success">
                        <h1>🎉 Поздравляем!</h1>
                        <h2>Вы успешно записались на курс "%s"</h2>
                        <p>Курс уже доступен в вашем профиле.</p>
                        <div style="margin-top: 2rem;">
                            <a href="/profile.html" style="display: inline-block; padding: 1rem 2rem; background: #3498db; color: white; text-decoration: none; border-radius: 5px; margin-right: 1rem;">Перейти в профиль</a>
                            <a href="/course/%d" style="display: inline-block; padding: 1rem 2rem; background: #2ecc71; color: white; text-decoration: none; border-radius: 5px;">Вернуться к курсу</a>
                        </div>
                    </div>
                </body>
                </html>
                """, course.getTitle(), courseId);
        }

        return getNotFoundPage();
    }

    private static String getStagePage(Long courseId, int stageNumber) {
        List<LearningStage> stages = courseStages.get(courseId);
        if (stages == null || stageNumber < 1 || stageNumber > stages.size()) {
            return getNotFoundPage();
        }

        LearningStage stage = stages.get(stageNumber - 1);

        return String.format("""
            HTTP/1.1 200 OK\r
            Content-Type: application/json\r
            \r
            {
                "courseId": %d,
                "stageNumber": %d,
                "title": "%s",
                "description": "%s",
                "content": "%s",
                "icon": "%s"
            }
            """, courseId, stageNumber,
                escapeJson(stage.getTitle()),
                escapeJson(stage.getDescription()),
                escapeJson(stage.getContent()),
                escapeJson(stage.getIcon()));
    }

    private static String getCoursesJson(boolean hasDiscount) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            json.append(String.format("""
                {
                    "id": %d,
                    "title": "%s",
                    "description": "%s",
                    "price": %.2f,
                    "level": "%s",
                    "image": "%s",
                    "instructor": "%s"
                }""",
                    course.getId(),
                    escapeJson(course.getTitle()),
                    escapeJson(course.getDescription()),
                    course.getPrice(hasDiscount),
                    escapeJson(course.getLevel()),
                    escapeJson(course.getImageUrl()),
                    escapeJson(course.getInstructor())));

            if (i < courses.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                json.toString();
    }

    private static String getStagesJson(Long courseId) {
        List<LearningStage> stages = courseStages.get(courseId);
        if (stages == null || stages.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < stages.size(); i++) {
            LearningStage stage = stages.get(i);
            json.append(String.format("""
                {
                    "number": %d,
                    "title": "%s",
                    "description": "%s",
                    "content": "%s",
                    "icon": "%s"
                }""",
                    stage.getNumber(),
                    escapeJson(stage.getTitle()),
                    escapeJson(stage.getDescription()),
                    escapeJson(stage.getContent()),
                    escapeJson(stage.getIcon())));

            if (i < stages.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        return json.toString();
    }

    private static String getFavicon() {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/x-icon\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n";
    }

    private static String getNotFoundPage() {
        return "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>404 Not Found</title>
                    <style>
                        body { font-family: Arial; padding: 2rem; text-align: center; background: #f5f5f5; }
                        h1 { color: #e74c3c; }
                    </style>
                </head>
                <body>
                    <h1>404 - Страница не найдена</h1>
                    <p><a href="/">Вернуться на главную</a></p>
                </body>
                </html>
                """;
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> params = new HashMap<>();
        if (formData != null && !formData.isEmpty()) {
            try {
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return params;
    }

    private static String getSessionId(Map<String, String> headers) {
        String cookie = headers.get("Cookie");
        if (cookie != null) {
            String[] cookies = cookie.split("; ");
            for (String c : cookies) {
                if (c.startsWith("sessionId=")) {
                    return c.substring("sessionId=".length());
                }
            }
        }
        return null;
    }

    private static User getUserFromSession(String sessionId) {
        if (sessionId == null) return null;

        String email = sessions.get(sessionId);
        if (email == null) return null;

        for (User user : users) {
            if (user.getEmail().equals(email)) {
                return user;
            }
        }
        return null;
    }

    private static Course findCourseById(Long id) {
        for (Course course : courses) {
            if (course.getId().equals(id)) {
                return course;
            }
        }
        return null;
    }

    private static String getLevelBadge(String level) {
        switch (level.toLowerCase()) {
            case "начинающий": return "🎯 Начинающий";
            case "средний": return "⭐ Средний";
            case "продвинутый": return "🚀 Продвинутый";
            default: return level;
        }
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ДАННЫХ ====================

    private static void initializeSampleData() {
        // Добавляем пользователей
        users.add(new User(1L, "admin@photography.ru", "admin123", "Администратор", "ADMIN", true));
        users.add(new User(2L, "user@test.ru", "user123", "Иван Иванов", "STUDENT", false));

        // Добавляем курсы с картинками (оригинальная цена 1,000,000 руб)
        courses.add(new Course(1L,
                "Основы фотографии для начинающих",
                "Идеальный курс для тех, кто только начинает свой путь в фотографии. Вы научитесь работать с камерой, понимать экспозицию и композицию.",
                "Начинающий", 1000000.0,
                "https://cameralabs.org/media/k2/items/cache/5a05b5284663ecccd84d3245147271aa_L.jpg",
                "Александр Петров", 8));
        courses.add(new Course(2L,
                "Портретная фотосъемка",
                "Научитесь создавать профессиональные портреты. Работа со светом, позами и эмоциями модели.",
                "Средний", 1000000.0,
                "https://i.pinimg.com/600x315/a2/7f/77/a27f7735e27392804fcc4cf90df37c1f.jpg",
                "Мария Сидорова", 12));

        courses.add(new Course(3L,
                "Пейзажная фотография",
                "Секреты съемки потрясающих пейзажей. Работа с естественным светом и композицией.",
                "Начинающий", 1000000.0,
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR6JY_mHyUs_BhiYTIRiL57RqLBllLtt2dWYQ&s.jpg",
                "Дмитрий Козлов", 10));

        courses.add(new Course(4L,
                "Студийная съемка",
                "Профессиональная работа в фотостудии. Оборудование, световые схемы, работа с моделью.",
                "Продвинутый", 1000000.0,
                "https://svobodnyy.vgorode.pro/uploads/catalogFiles/d14efadb-312a-477c-8cf5-acd353ab80ff_midl.jpg",
                "Ольга Николаева", 16));

        courses.add(new Course(5L,
                "Обработка в Photoshop",
                "Научитесь обрабатывать фотографии как профессионал. Ретушь, цветокоррекция, работа со слоями.",
                "Средний", 1000000.0,
                "https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/Adobe_Photoshop_CS6_icon.svg/1280px-Adobe_Photoshop_CS6_icon.svg.png",
                "Андрей Волков", 8));

        // Создаем этапы обучения для каждого курса
        createLearningStages();

        System.out.println("Инициализировано " + users.size() + " пользователей и " + courses.size() + " курсов");
    }

    private static void createLearningStages() {
        // Этапы для курса "Основы фотографии"
        List<LearningStage> stages1 = new ArrayList<>();
        stages1.add(new LearningStage(1, "Знакомство с фотоаппаратом",
                "Устройство камеры и основные настройки",
                "В этом уроке вы узнаете:<br><br>" +
                        "• Основные элементы фотоаппарата<br>" +
                        "• Виды объективов и их назначение<br>" +
                        "• Режимы съемки: авто, приоритет выдержки, приоритет диафрагмы<br>" +
                        "• Форматы файлов: JPEG vs RAW",
                ""));

        stages1.add(new LearningStage(2, "Экспозиция: выдержка, диафрагма, ISO",
                "Три кита правильной экспозиции",
                "Подробно разберем:<br><br>" +
                        "• Что такое экспозиция и экспопара<br>" +
                        "• Выдержка: от 1/4000 до 30 секунд<br>" +
                        "• Диафрагма: от f/1.4 до f/22<br>" +
                        "• ISO: чувствительность матрицы<br>" +
                        "• Треугольник экспозиции",
                "⚡"));

        for (int i = 3; i <= 10; i++) {
            stages1.add(new LearningStage(i,
                    "Этап " + i,
                    "Описание этапа " + i,
                    "Содержание этапа " + i + ". Подробная информация и задания.",
                    ""));
        }

        courseStages.put(1L, stages1);

        for (long i = 2; i <= 5; i++) {
            List<LearningStage> stages = new ArrayList<>();
            for (int j = 1; j <= 10; j++) {
                stages.add(new LearningStage(j,
                        "Этап " + j + " курса",
                        "Описание этапа " + j,
                        "Содержание этапа " + j + ". Здесь будет подробная информация об этом этапе обучения.",
                        ""));
            }
            courseStages.put(i, stages);
        }
    }
}

