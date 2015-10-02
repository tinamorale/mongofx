package mongoui.ui.main;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleSpans;
import org.fxmisc.richtext.StyleSpansBuilder;
import org.fxmisc.wellbehaved.event.EventHandlerHelper;
import org.fxmisc.wellbehaved.event.EventHandlerHelper.Builder;

import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyEvent;

public class CodeAreaBuilder {
  private static final String[] KEYWORDS = new String[]{
    "db"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String PAREN_PATTERN = "\\(|\\)";
  private static final String BRACE_PATTERN = "\\{|\\}";
  private static final String BRACKET_PATTERN = "\\[|\\]";
  private static final String SEMICOLON_PATTERN = "\\;";
  private static final String STRING_PATTERN_DOUBLE = "\"([^\"\\\\]|\\\\.)*\"";
  private static final String STRING_PATTERN_SINGLE = "'([^'\\\\]|\\\\.)*'";
  private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

  private static final Pattern PATTERN = Pattern.compile("(?<KEYWORD>" + KEYWORD_PATTERN + ")" //
      + "|(?<PAREN>" + PAREN_PATTERN + ")" //
      + "|(?<BRACE>" + BRACE_PATTERN + ")" //
      + "|(?<BRACKET>" + BRACKET_PATTERN + ")" //
      + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")" //
      + "|(?<STRINGDOUBLE>" + STRING_PATTERN_DOUBLE + ")" //
      + "|(?<STRINGSINGLE>" + STRING_PATTERN_SINGLE + ")" //
      + "|(?<COMMENT>" + COMMENT_PATTERN + ")" //
  );

  public static void setup(CodeArea codeArea) {
    codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      codeArea.setStyleSpans(0, computeHighlighting(newText));
    });

    Builder<KeyEvent> onKeyTyped = EventHandlerHelper.startWith(e -> {
      String character = e.getCharacter();
      if ("{".equals(character)) {
        charRight(codeArea, "}");
      }
      else if ("[".equals(character)) {
        charRight(codeArea, "]");
      }
    });
    codeArea.setOnKeyTyped(onKeyTyped.create());
  }

  private static void charRight(CodeArea codeArea, String ch) {
    IndexRange selection = codeArea.getSelection();
    codeArea.insertText(selection.getStart(), ch);
    codeArea.selectRange(selection.getStart(), selection.getStart());
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass = //
          matcher.group("KEYWORD") != null ? "keyword" : //
              matcher.group("PAREN") != null ? "paren" : //
                  matcher.group("BRACE") != null ? "brace" : //
                      matcher.group("BRACKET") != null ? "bracket" : //
                          matcher.group("SEMICOLON") != null ? "semicolon" : //
                              matcher.group("STRINGSINGLE") != null ? "string" : //
                                  matcher.group("STRINGDOUBLE") != null ? "string" : //
                                      matcher.group("COMMENT") != null ? "comment" : //
                                          null;
      /* never happens */ assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
