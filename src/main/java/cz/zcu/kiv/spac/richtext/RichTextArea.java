package cz.zcu.kiv.spac.richtext;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.StyledTextArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.reactfx.Observable;
import org.reactfx.SuspendableNo;
import org.reactfx.collection.LiveList;
import org.reactfx.util.Either;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.text.Segment;

public class RichTextArea extends VBox {

    private final TextOps<String, TextStyle> styledTextOps = SegmentOps.styledTextOps();
    private final LinkedImageOps<TextStyle> linkedImageOps = new LinkedImageOps<>();
    private Map<Either, String> imagesPath = new HashMap<>();

    private Stage mainStage;

    private final SuspendableNo updatingToolbar = new SuspendableNo();

    private final GenericStyledArea<ParStyle, Either<String, LinkedImage>, TextStyle> area =
            new GenericStyledArea<>(
                    ParStyle.EMPTY,                                                 // default paragraph style
                    (txtFlow,pstyle) -> txtFlow.setStyle(pstyle.toCss()),        	// paragraph style setter

                    TextStyle.EMPTY.updateFontSize(12).updateFontFamily("Serif").updateTextColor(Color.BLACK),  // default segment style
                    styledTextOps._or(linkedImageOps, (s1, s2) -> Optional.empty()),                            // segment operations
                    seg -> createNode(seg, (text, style) -> text.setStyle(style.toCss())));                   // Node creator and segment style setter
    {
        area.setWrapText(true);
        area.setStyleCodecs
                (
                        ParStyle.CODEC, Codec.styledSegmentCodec
                                (
                                        Codec.eitherCodec(Codec.STRING_CODEC, LinkedImage.codec()), TextStyle.CODEC
                                )
                );
        area.setParagraphGraphicFactory( new BulletFactory( area ) );
    }

    public RichTextArea(Stage primaryStage) {

        mainStage = primaryStage;

        Button boldBtn = createButton("bold", this::toggleBold, "Bold");
        Button italicBtn = createButton("italic", this::toggleItalic, "Italic");
        Button underlineBtn = createButton("underline", this::toggleUnderline, "Underline");
        Button insertImageBtn = createButton("insertimage", this::insertImage, "Insert Image");
        Button increaseIndentBtn = createButton("increaseIndent", this::increaseIndent, "Increase indent");
        Button decreaseIndentBtn = createButton("decreaseIndent", this::decreaseIndent, "Decrease indent");

        area.beingUpdatedProperty().addListener((o, old, beingUpdated) -> {

            if(!beingUpdated) {

                boolean bold, italic, underline;

                IndexRange selection = area.getSelection();
                if(selection.getLength() != 0) {

                    StyleSpans<TextStyle> styles = area.getStyleSpans(selection);
                    bold = styles.styleStream().anyMatch(s -> s.bold.orElse(false));
                    italic = styles.styleStream().anyMatch(s -> s.italic.orElse(false));
                    underline = styles.styleStream().anyMatch(s -> s.underline.orElse(false));

                } else {

                    int p = area.getCurrentParagraph();
                    int col = area.getCaretColumn();
                    TextStyle style = area.getStyleAtPosition(p, col);
                    bold = style.bold.orElse(false);
                    italic = style.italic.orElse(false);
                    underline = style.underline.orElse(false);
                }

                int startPar = area.offsetToPosition(selection.getStart(), Forward).getMajor();
                int endPar = area.offsetToPosition(selection.getEnd(), Backward).getMajor();
                List<Paragraph<ParStyle, Either<String, LinkedImage>, TextStyle>> pars = area.getParagraphs().subList(startPar, endPar + 1);

                updatingToolbar.suspendWhile(() -> {

                    if(bold) {

                        if(!boldBtn.getStyleClass().contains("pressed")) {

                            boldBtn.getStyleClass().add("pressed");
                        }

                    } else {

                        boldBtn.getStyleClass().remove("pressed");
                    }

                    if(italic) {

                        if(!italicBtn.getStyleClass().contains("pressed")) {

                            italicBtn.getStyleClass().add("pressed");
                        }

                    } else {

                        italicBtn.getStyleClass().remove("pressed");
                    }

                    if(underline) {

                        if(!underlineBtn.getStyleClass().contains("pressed")) {

                            underlineBtn.getStyleClass().add("pressed");
                        }

                    } else {

                        underlineBtn.getStyleClass().remove("pressed");
                    }
                });
            }
        });

        ToolBar toolBar1 = new ToolBar(
                boldBtn, italicBtn, underlineBtn, new Separator(Orientation.VERTICAL),
                increaseIndentBtn, decreaseIndentBtn, new Separator(Orientation.VERTICAL),
                insertImageBtn);

        VirtualizedScrollPane<GenericStyledArea<ParStyle, Either<String, LinkedImage>, TextStyle>> vsPane = new VirtualizedScrollPane<>(area);

        VBox.setVgrow(vsPane, Priority.ALWAYS);
        this.getChildren().addAll(toolBar1, vsPane);
    }

    private Node createNode(StyledSegment<Either<String, LinkedImage>, TextStyle> seg,
                            BiConsumer<? super TextExt, TextStyle> applyStyle) {

        return seg.getSegment().unify(
                text -> StyledTextArea.createStyledTextNode(text, seg.getStyle(), applyStyle),
                LinkedImage::createNode
        );
    }

    private Button createButton(String styleClass, Runnable action, String toolTip) {

        Button button = new Button();
        button.getStyleClass().add(styleClass);
        button.setOnAction(evt -> {
            action.run();
            area.requestFocus();
        });
        button.setPrefWidth(25);
        button.setPrefHeight(25);
        if (toolTip != null) {
            button.setTooltip(new Tooltip(toolTip));
        }
        return button;
    }

    private void toggleBold() {

        updateStyleInSelection(spans -> TextStyle.bold(!spans.styleStream().allMatch(style -> style.bold.orElse(false))));
    }

    private void toggleItalic() {

        updateStyleInSelection(spans -> TextStyle.italic(!spans.styleStream().allMatch(style -> style.italic.orElse(false))));
    }

    private void toggleUnderline() {

        updateStyleInSelection(spans -> TextStyle.underline(!spans.styleStream().allMatch(style -> style.underline.orElse(false))));
    }

    /**
     * Action listener which inserts a new image at the current caret position.
     */
    private void insertImage() {

        String initialDir = System.getProperty("user.dir");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Insert image");
        fileChooser.setInitialDirectory(new File(initialDir));
        File selectedFile = fileChooser.showOpenDialog(mainStage);
        if (selectedFile != null) {
            String imagePath = selectedFile.getAbsolutePath();
            imagePath = imagePath.replace('\\',  '/');

            // Create new image segment from image.
            Either segment = Either.right(new RealLinkedImage(imagePath));

            // Create ReadOnlyStyledDocument for displaying it in rich textarea.
            ReadOnlyStyledDocument<ParStyle, Either<String, LinkedImage>, TextStyle> ros =
                    ReadOnlyStyledDocument.fromSegment(segment, ParStyle.EMPTY, TextStyle.EMPTY, area.getSegOps());

            // Also put this segment to map for extracting image path.
            // Maybe it can be done by parsing something, but i'm not sure it is possible (see parsing content).
            imagesPath.put(segment, imagePath);
            area.replaceSelection(ros);

        }
    }

    private void increaseIndent() {

        updateParagraphStyleInSelection(ParStyle::increaseIndent);
    }

    private void decreaseIndent() {

        parseContent();
        updateParagraphStyleInSelection(ParStyle::decreaseIndent);
    }

    private void parseContent() {

        // TODO: získání hodnot už mám, teď to mrdnout do nějakýho objektu který se vrátí z týhle metody.
        // TODO: možná to rovnou přetransformovat do markdownu.

        // Paragraph -> single line.
        // Segment -> Single text with properties or image.
        // Segment as text -> "temporary <b>text</b>" -> 2 segments, where first contains "temporary" and second contains
        // "text" with bold style. <b></b> is just for representing bold in this comment.
        LiveList<Paragraph<ParStyle, Either<String, LinkedImage>, TextStyle>> paragraphs = area.getContent().getParagraphs();

        for (Paragraph paragraph : paragraphs) {

            // Get segments in paragraph and paragraph style.
            List segments = paragraph.getStyledSegments();
            ParStyle paragraphStyle = (ParStyle) paragraph.getParagraphStyle();

            // Get paragraph indent.
            Optional<Indent> indent = paragraphStyle.indent;

            for (Object oSegment : segments) {

                StyledSegment styledSegment = (StyledSegment) oSegment;
                TextStyle textStyle = (TextStyle) styledSegment.getStyle();

                // Bold / Italic / Underline properties.
                Optional<Boolean> bold = textStyle.bold;
                Optional<Boolean> italic = textStyle.italic;
                Optional<Boolean> underline = textStyle.underline;

                // Parse image if there is any.
                Either segment = (Either) styledSegment.getSegment();
                String imagePath = imagesPath.get(segment);

                System.out.println();
            }

            System.out.println();
        }

        // V každym paragraphu může být víc segmentů
        // Par[;
        // StyledSegment(segment=left(cd ) style=12,Serif,0x000000ff),
        // StyledSegment(segment=right(RealLinkedImage[path=C:/Users/danisik/Desktop/OP/newProject/Software-process-antipatterns-catalogue/catalogue/Collective_Procrastination_-_burndown_example.png]) style=)
        // ]
    }

    private void updateStyleInSelection(Function<StyleSpans<TextStyle>, TextStyle> mixinGetter) {

        IndexRange selection = area.getSelection();
        if(selection.getLength() != 0) {
            StyleSpans<TextStyle> styles = area.getStyleSpans(selection);
            TextStyle mixin = mixinGetter.apply(styles);
            StyleSpans<TextStyle> newStyles = styles.mapStyles(style -> style.updateWith(mixin));
            area.setStyleSpans(selection.getStart(), newStyles);
        }
    }

    private void updateParagraphStyleInSelection(Function<ParStyle, ParStyle> updater) {

        IndexRange selection = area.getSelection();
        int startPar = area.offsetToPosition(selection.getStart(), Forward).getMajor();
        int endPar = area.offsetToPosition(selection.getEnd(), Backward).getMajor();
        for(int i = startPar; i <= endPar; ++i) {
            Paragraph<ParStyle, Either<String, LinkedImage>, TextStyle> paragraph = area.getParagraph(i);
            area.setParagraphStyle(i, updater.apply(paragraph.getParagraphStyle()));
        }
    }
}
