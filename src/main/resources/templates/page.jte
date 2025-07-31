@import gg.jte.Content
@import net.derfruhling.spacemaven.LocalizationContext
@import java.util.Objects

@param String title
@param LocalizationContext locale
@param Content head = null
@param Content content

<!DOCTYPE html>

<html lang="${locale.getLanguage()}">
<head>
    <title>${Objects.requireNonNullElse(title, "Spacemaven")}</title>

    <link rel="stylesheet" href="/static/style.css">

    @if (head != null)
        ${head}
    @endif
</head>

<body>

<h1>${locale.lookup("index.header")}</h1>

<p>${locale.lookup("index.description")}</p>

<div class="content">${content}</div>

</body>
</html>