package priv.lin.plugin.fish.reader.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author linnan 2024-11-20
 * @since
 */
public class ReaderToolWindowFactory implements ToolWindowFactory {
    /**
     * 缓存CSS文件内容的Map
     */
    private final Map<String, String> cssCache = new HashMap<>();
    /**
     * 上次访问的url 默认访问书架
     */
    private String lastVisitedUrl = "https://weread.qq.com/web/shelf";

    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
        // JBCefApp 判断是否支持
        if (!JBCefApp.isSupported()) {
            // 不支持就输出一句提示
            JLabel notSupportedLabel = new JLabel();
            notSupportedLabel.setText("不支持此插件: see https://plugins.jetbrains.com/docs/intellij/jcef.html#jbcefapp");
            panel.add(notSupportedLabel);
            return;
        } // 使用持久化存储的 URL 或默认 URL
        lastVisitedUrl = PropertiesComponent.getInstance().getValue("lastVisitedUrl", lastVisitedUrl);

        JBCefBrowser jbCefBrowser = new JBCefBrowser(lastVisitedUrl);
        jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                // 更新 上次访问地址
                lastVisitedUrl = browser.getURL();
                PropertiesComponent.getInstance().setValue("lastVisitedUrl", lastVisitedUrl);
                // 根据 URL 更新样式
                String category = determineCategory(browser.getURL());
                updateStyle(browser, category);
            }
        }, jbCefBrowser.getCefBrowser());
        panel.add(jbCefBrowser.getComponent(), BorderLayout.CENTER);
    }

    private String determineCategory(String url) {
        if (url.equals("https://weread.qq.com/web/shelf") || url.startsWith("https://weread.qq.com/web/shelf/")) {
            return "shelf";
        } else if (url.startsWith("https://weread.qq.com/web/reader/")) {
            return "reader";
        } else if (url.equals("https://weread.qq.com") || url.startsWith("https://weread.qq.com/")) {
            return "homepage";
        }
        return "homepage";
    }

    private void updateStyle(CefBrowser browser, String category) {
        String cssContent = readCssFile(category);
        if (cssContent == null) {
            return;
        }

        // 执行 JavaScript 来设置样式
        browser.executeJavaScript("(function() {" +
                "    var style = document.createElement('style');" +
                "    style.type = 'text/css';" +
                "    style.innerHTML = '" + cssContent + "';" +
                "    if (document.head) {" +
                "        document.head.appendChild(style);" +
                "    } else {" +
                "        document.addEventListener('DOMContentLoaded', function() {" +
                "            document.head.appendChild(style);" +
                "        });" +
                "    }" +
                "})();", browser.getURL(), 0);
    }

    private String readCssFile(String category) {
        // 首先检查缓存中是否有该CSS文件的内容
        if (cssCache.containsKey(category)) {
            return cssCache.get(category);
        }

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("css/" + category + ".css");
        if (inputStream == null) {
            return null;
        }

        StringBuilder cssContent = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                cssContent.append(line).append("\n");
            }
            String css = cssContent.toString();
            css = css.replace("'", "\\'")
                    .replace("\n", "\t");
            // 获取当前主题的背景色
            Color backgroundColor = UIManager.getColor("Panel.background");
            // 获取当前主题的注释字体颜色 假设注释颜色与禁用的标签前景色相同
            Color fontColor = UIManager.getColor("Label.disabledForeground");
            // 替换文件中的占位符 ${backgroundColor} ${fontColor}
            css = css.replace("${backgroundColor}", String.format("%d, %d, %d", backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue()))
                    .replace("${fontColor}", String.format("%d, %d, %d", fontColor.getRed(), fontColor.getGreen(), fontColor.getBlue()));
            // 读取完毕后，将内容存入缓存
            cssCache.put(category, css);
        } catch (IOException e) {
            return null;
        }
        return cssCache.get(category);
    }
}