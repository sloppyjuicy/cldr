package org.unicode.cldr.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.VettingViewer;

public class SurveyTool extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final boolean USE_DOJO = true;

    @Override
    public final void init(final ServletConfig config) throws ServletException {
        System.out.println("\n🍓🍓🍓 SurveyTool.init() 🍓🍓🍓\n");
        try {
            super.init(config);
        } catch (Throwable t) {
            System.err.println("SurveyTool.init() caught: " + t.toString());
            return;
        }
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        serveSinglePageApp((HttpServletRequest) request, (HttpServletResponse) response);
    }

    /**
     * Serve the HTML for Survey Tool
     *
     * @param request
     * @param response
     * @throws IOException
     *
     * Serve four different versions of the html page:
     *   1. Busted/Offline
     *   2. Starting/Waiting
     *   3. Problem (session==null)
     *   4. Running normally
     *
     * Plan: reduce html dynamically generated by back end (Java); leave presentation to be done by the front end (JavaScript)
     */
    private void serveSinglePageApp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SurveyMain sm = SurveyMain.getInstance(request);
        PrintWriter out = response.getWriter();
        out.write("<!DOCTYPE html>\n");
        if (SurveyMain.isBusted != null || request.getParameter("_BUSTED") != null) {
            serveBustedPage(request, out);
        } else if (sm == null || !SurveyMain.isSetup || request.getParameter("_STARTINGUP") != null) {
            serveWaitingPage(request, out, sm);
        } else {
            /*
             * TODO: clarify whether calling new WebContext and setSession is appropriate here.
             * This is how it was with the old v.jsp. However, setSession has a comment saying it should only
             * be called once. Should we get an existing ctx and status from SurveyMain?
             */
            WebContext ctx = new WebContext(request, response);
            request.setAttribute("WebContext", ctx);
            ctx.setSessionMessage(null); // ??
            ctx.setSession();
            if (ctx.session == null) {
                serveProblemNoSessionPage(request, out, ctx.getSessionMessage());
            } else {
                serveRunnningNormallyPage(request, out, sm);
            }
        }
    }

    private void serveBustedPage(HttpServletRequest request, PrintWriter out) {
        out.write("<html>\n<head>\n");
        out.write("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n");
        out.write("<title>CLDR Survey Tool | Offline</title>\n");
        includeCss(request, out);
        out.write("</head>\n<body>\n");
        out.write("<p class='ferrorbox'>Survey Tool is offline</p>\n");
        out.write("</body>\n</html>\n");
    }

    private void serveWaitingPage(HttpServletRequest request, PrintWriter out, SurveyMain sm)
            throws IOException {
        /*
         * TODO: simplify serveWaitingPage and/or move it to the front end.
         * This is a crude port from old v.jsp, with js inside html inside java.
         * It could be the same as serveRunnningNormallyPage, except that
         * instead of 'st-run-gui' it would have 'st-wait-for-server', and then
         * the front end could be in charge of the JavaScript.
         */
        String url = request.getContextPath() + request.getServletPath();
        out.write("<html lang='" + SurveyMain.TRANS_HINT_LOCALE.toLanguageTag() + " class='claro'>\n");
        out.write("<head>\n");
        out.write("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n");
        out.write("<title>CLDR Survey Tool | Starting</title>\n");
        includeCss(request, out);
        if (request.getParameter("_STARTINGUP") == null) {
            writeTimeoutReloadScript(out, url);
        }
        writeDotDotDotScript(out);
        out.write(VettingViewer.getHeaderStyles() + "\n");
        try {
            includeJavaScript(request, out);
        } catch (JSONException e) {
            SurveyLog.logException(e, "Including JavaScript");
        }
        out.write("</head>\n<body>\n");
        writeWaitingNavbarHtml(out);
        out.write("<div class='container'>\n");
        out.write("  <div class='starter-template' style='margin-top: 120px;'>\n");
        out.write("<h1>Waiting for the Survey Tool to come online<span id='dots'>...</span></h1>\n");
        out.write("<p class='lead'>The Survey Tool may be starting up.  </p>\n");
        if (SurveyMain.isUnofficial()) {
            out.write("<p><span class='glyphicon glyphicon-wrench'></span>"
                + SurveyMain.getCurrev(true) + "</p>\n");
        }
        out.write("If you are not redirected in a minute or two, please click\n");
        final String survURL = request.getContextPath() + "/survey";
        out.write("<a id='redir2' href='" + survURL + "'>this link</a> to try again.\n");
        if (request.getParameter("_STARTINGUP") == null) {
            out.write("<script>\n");
            out.write("var newUrl = '" + url + "' + document.location.search +  document.location.hash;\n");
            out.write("var survURL = '" + survURL + "';\n");
            out.write("(document.getElementById('redir') || {}).href = newUrl;\n");
            out.write("var dstatus = document.getElementById('st_err');\n");
            out.write("if (dstatus != null) {\n");
            out.write("  dstatus.appendChild(document.createElement('br'));\n");
            out.write("  dstatus.appendChild(document.createTextNode('.'));\n");
            if (USE_DOJO) {
                out.write("  require(['dojo/ready'], function(ready) {\n");
                out.write("    ready(function() {\n");
            } else {
                out.write("  $(function() {\n"); // jquery
            }
            out.write("        dstatus.appendChild(document.createTextNode('.'));\n");
            out.write("        window.setTimeout(function () {\n");
            out.write("          dstatus.appendChild(document.createTextNode('.'));\n");
            out.write("          const load = function (data) {\n");
            out.write("            dstatus.appendChild(\n");
            out.write("              document.createTextNode(\n");
            out.write("                'Loaded ' +\n");
            out.write("                  data.length +\n");
            out.write("                  ' bytes from SurveyTool. Reloading this page..'\n");
            out.write("              )\n");
            out.write("            );\n");
            out.write("            window.location.reload(true);\n");
            out.write("          };\n");
            out.write("          const xhrArgs = {\n");
            out.write("            url: survURL,\n");
            out.write("            load: load,\n");
            out.write("          };\n");
            out.write("          cldrAjax.sendXhr(xhrArgs);\n");
            out.write("        }, 2000); // two seconds\n");
            out.write("      });\n");
            out.write("    }\n");
            if (USE_DOJO) {
                out.write("  });\n");
            }
            out.write("}\n");
            out.write("</script>\n");
        }
        if (sm != null) {
            String htmlStatus = sm.startupThread.htmlStatus();
            if (htmlStatus != null) {
                out.write(htmlStatus);
            }
        }
        out.write("<hr>\n");
        out.write("<div id='st_err'></div>\n</div>\n</div>\n");
        out.write("</body>\n</html>\n");
    }

    private void writeTimeoutReloadScript(PrintWriter out, String url) {
        out.write("<script>\n");
        out.write("  window.setTimeout(function() {\n");
        out.write("    window.location.reload(true);\n");
        out.write("    //document.location='" + url
                    + "' + document.location.search + document.location.hash;\n");
        out.write("  },10000 /* ten seconds */);\n");
        out.write("</script>\n");
    }

    private void writeDotDotDotScript(PrintWriter out) {
        out.write("<script>\n");
        out.write("var spin0 = 0;\n");
        out.write("window.setInterval(function() {\n");
        out.write("    spin0 = (spin0+1)%3;\n");
        out.write("    var dots = document.getElementById('dots');\n");
        out.write("    if (dots) {\n");
        out.write("        switch(spin0) {\n");
        out.write("         case 0:\n");
        out.write("             dots.innerHTML = '.';\n");
        out.write("             break;\n");
        out.write("         case 1:\n");
        out.write("             dots.innerHTML='&nbsp;.';\n");
        out.write("             break;\n");
        out.write("         case 2:\n");
        out.write("            dots.innerHTML='&nbsp;&nbsp;.';\n");
        out.write("           break;\n");
        out.write("        }\n");
        out.write("    }\n");
        out.write("}, 1000);\n");
        out.write("</script>\n");
    }

    private void writeWaitingNavbarHtml(PrintWriter out) {
        out.write("<div class=\"navbar navbar-fixed-top\" role=\"navigation\">\n"
            + "  <div class=\"container\">\n"
            + "    <div class=\"navbar-header\">\n"
            + "      <p class=\"navbar-brand\">\n"
            + "        <a href=\"http://cldr.unicode.org\">CLDR</a> SurveyTool\n"
            + "      </p>\n"
            + "    </div>\n"
            + "    <div class=\"collapse navbar-collapse  navbar-right\">\n"
            + "      <ul class=\"nav navbar-nav\">\n"
            + "        <li><a href=\"http://cldr.unicode.org/index/survey-tool\">Help</a></li>\n"
            + "      </ul>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "</div>\n");
    }

    private void serveProblemNoSessionPage(HttpServletRequest request, PrintWriter out, String sessionMessage) {
        out.write("<html class='claro'>\n<head class='claro'>\n");
        out.write("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n");
        out.write("<title>CLDR Survey Tool</title>\n");
        out.write("</head>\n<body>\n");
        out.write("<p class='ferrorbox'>Survey Tool is offline</p>\n");
        out.write("<div style='float: right'>\n");
        out.write("  <a href='" + request.getContextPath() + "/login.jsp'"
            + " id='loginlink' class='notselected'>Login…</a>\n");
        out.write("</div>\n");
        out.write("<h2>CLDR Survey Tool | Problem</h2>\n");
        out.write("<div>\n");
        out.write("<p><img src='stop.png' width='16'>" + sessionMessage + "</p>\n");
        out.write("</div>\n");
        out.write("<hr>\n");
        out.write("<p><" + SurveyMain.getGuestsAndUsers() + "</p>\n");
        out.write("</body>\n</html>\n");
    }

    private void serveRunnningNormallyPage(HttpServletRequest request, PrintWriter out, SurveyMain sm)
            throws IOException {
        String lang = SurveyMain.TRANS_HINT_LOCALE.toLanguageTag();
        out.write("<html lang='" + lang + "' class='claro'>\n");
        out.write("<head>\n");
        out.write("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n");
        out.write("<title>CLDR Survey Tool</title>\n");
        out.write("<meta name='robots' content='noindex,nofollow'>\n");
        out.write("<meta name='gigabot' content='noindex'>\n");
        out.write("<meta name='gigabot' content='noarchive'>\n");
        out.write("<meta name='gigabot' content='nofollow'>\n");
        includeCss(request, out);
        out.write(VettingViewer.getHeaderStyles() + "\n");
        try {
            includeJavaScript(request, out);
        } catch (JSONException e) {
            SurveyLog.logException(e, "Including JavaScript");
        }
        out.write("</head>\n");
        out.write("<body lang='" + lang + "' data-spy='scroll' data-target='#itemInfo'>\n");
        out.write("<p id='st-run-gui'>Loading...</p>\n");
        if (!USE_DOJO) {
            out.write("<script>cldrGui.run()</script>\n");
        }
        out.write("</body>\n</html>\n");
    }

    private void includeCss(HttpServletRequest request, PrintWriter out) {
        String contextPath = request.getContextPath();
        out.write("<link rel='stylesheet' href='" + contextPath + "/surveytool.css' />\n");
        out.write("<link rel='stylesheet' href='" + contextPath + "/css/CldrStForum.css' />\n");
        if (USE_DOJO) {
            out.write("<link rel='stylesheet' href='//ajax.googleapis.com/ajax/libs/dojo/1.14.1/dijit/themes/claro/claro.css' />\n");
        }
        out.write("<link rel='stylesheet' href='//stackpath.bootstrapcdn.com/bootswatch/3.1.1/spacelab/bootstrap.min.css' />\n");
        out.write("<link rel='stylesheet' href='" + contextPath + "/css/redesign.css' />\n");
    }

    /**
     * Write the script tags for Survey Tool JavaScript files
     *
     * @param request the HttpServletRequest
     * @param out the Writer
     * @throws IOException
     * @throws JSONException
     */
    public static void includeJavaScript(HttpServletRequest request, Writer out) throws IOException, JSONException {
        if (USE_DOJO) {
            includeDojoJavaScript(out);
        }
        includeJqueryJavaScript(out);
        includeCldrJavaScript(request, out);
    }

    private static void includeDojoJavaScript(Writer out) throws IOException {
        out.write("<script>dojoConfig = {parseOnLoad: false, async: true,};</script>\n");
        out.write("<script src='//ajax.googleapis.com/ajax/libs/dojo/1.14.1/dojo/dojo.js'></script>\n");
    }

    private static void includeJqueryJavaScript(Writer out) throws IOException {
        out.write("<script src='//ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js'></script>\n");
        out.write("<script src='//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/jquery-ui.min.js'></script>\n");
    }

    private static void includeCldrJavaScript(HttpServletRequest request, Writer out) throws IOException {
        final String prefix = "<script src='" + request.getContextPath() + "/js/";
        final String tail = "'></script>\n";
        final String js = getCacheBustingExtension(request) + ".js" + tail;

        out.write(prefix + "jquery.autosize.min.js" + tail); // exceptional

        out.write(prefix + "new/cldrText" + js); // new/cldrText.js -- same file for dojo and non-dojo
        out.write(prefix + "new/cldrStatus" + js); // new/cldrStatus.js -- same file for dojo and non-dojo
        out.write(prefix + "new/cldrAjax" + js); // new/cldrAjax.js -- same file for dojo and non-dojo

        if (USE_DOJO) {
            out.write(prefix + "CldrDojoBulkClosePosts" + js); // CldrDojoBulkClosePosts.js
            out.write(prefix + "CldrDojoForumParticipation" + js); // CldrDojoForumParticipation.js
            out.write(prefix + "new/cldrForumFilter" + js); // new/cldrForumFilter.js
            out.write(prefix + "new/cldrCsvFromTable" + js); // new/cldrCsvFromTable.js
            out.write(prefix + "CldrDojoDeferHelp" + js); // CldrDojoDeferHelp.js
            out.write(prefix + "CldrDojoForum" + js); // CldrDojoForum.js
            out.write(prefix + "survey" + js); // survey.js
            out.write(prefix + "CldrDojoLoad" + js); // CldrDojoLoad.js
            out.write(prefix + "CldrDojoTable" + js); // CldrDojoTable.js
            out.write(prefix + "bootstrap.min.js" + tail); // exceptional
            out.write(prefix + "redesign" + js); // redesign.js
            out.write(prefix + "review" + js); // review.js
            out.write(prefix + "CldrDojoGui" + js); // CldrGuiDojo.js
        } else {
            out.write(prefix + "new/cldrBulkClosePosts" + js); // new/cldrBulkClosePosts.js
            out.write(prefix + "new/cldrForumParticipation" + js); // new/cldrForumParticipation.js
            out.write(prefix + "new/cldrForumFilter" + js); // new/cldrForumFilter.js
            out.write(prefix + "new/cldrCsvFromTable" + js); // new/cldrCsvFromTable.js
            out.write(prefix + "new/cldrDeferHelp" + js); // new/cldrDeferHelp.js
            out.write(prefix + "new/cldrForum" + js); // new/cldrForum.js
            out.write(prefix + "new/cldrFlip" + js); // new/cldrFlip.js
            out.write(prefix + "new/cldrLocaleMap" + js); // new/cldrLocaleMap.js
            out.write(prefix + "new/cldrXpathMap" + js); // new/cldrXpathMap.js
            out.write(prefix + "new/cldrLoad" + js); // new/cldrLoad.js
            out.write(prefix + "new/cldrTable" + js); // new/cldrTable.js
            out.write(prefix + "bootstrap.min.js" + tail); // exceptional
            out.write(prefix + "new/cldrGui" + js); // new/cldrGui.js
        }
    }

    /**
     * The cache-busting filename extension, like "._b7a33e9fe_", to be used for those http requests
     * that employ the right kind of server configuration (as with nginx on the production server)
     */
    private static String cacheBustingExtension = null;

    /**
     * Get a string to be added to the filename, like "._b7a33e9f_", if we're responding to the kind
     * of request we get with nginx; else, get an empty string (no cache busting).
     *
     * If we're running with a reverse proxy (nginx), use "cache-busting" to make sure browser uses
     * the most recent JavaScript files.
     *
     * Change filename to be like "CldrStAjax._b7a33e9f_.js", instead of adding a query string,
     * like "CldrStAjax.js?v=b7a33e9fe", since a query string appears sometimes to be ignored by some
     * browsers. The server (nginx) needs a rewrite rule like this to remove the hexadecimal hash:
     *
     *     rewrite ^/(.+)\._[\da-f]+_\.(js|css)$ /$1.$2 break;
     *
     * Include underscores to avoid unwanted rewrite if we had a name like "example.bad.js",
     * where "bad" could be mistaken for a hexadecimal hash.
     *
     * @return a (possibly empty) string to be added to the filename
     */
    private static String getCacheBustingExtension(HttpServletRequest request) {
        if (request.getHeader("X-Real-IP") == null) {
            /*
             * Request wasn't made through nginx? Leave cacheBustingExtension alone, to enable
             * both kinds of request at the same time (with/without nginx) for debugging
             */
            return "";
        }
        if (cacheBustingExtension == null) {
            final String hash = CldrUtility.getCldrBaseDirHash();
            if (hash == null || !hash.matches("[0-9a-f]+")) {
                cacheBustingExtension = "";
            } else {
                cacheBustingExtension = "._" + hash.substring(0, 8) + "_";
            }
        }
        return cacheBustingExtension;
    }
}
