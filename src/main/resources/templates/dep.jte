@import net.derfruhling.spacemaven.LocalizationContext
@import net.derfruhling.spacemaven.SpecRef
@import java.util.Objects

@param LocalizationContext locale
@param SpecRef spec

@template.page(title = spec.getFullyQualifiedName(), locale = locale, head = @`
    <meta name="spm:repoName" content="${spec.getRepository()}">
    <meta name="spm:repoUrl" content="${spec.getRepositoryUrl()}">
    <meta name="spm:origin" content="${spec.getFullyQualifiedName()}">
    <meta name="spm:latest" content="${Objects.requireNonNullElse(spec.getLatestVersion(), "null")}">
    <meta name="spm:latestRelease" content="${Objects.requireNonNullElse(spec.getLatestReleaseVersion(), "null")}">

    <style>
        .horizontal-rule {
            border: 0 solid black;
            border-bottom-width: 1px;
            width: 300px;
            max-width: 100%;
            margin: 15px 5px;
        }

        li::marker {
            content: '>  ';
        }
    </style>
`, content = @`
    <div class="horizontal-rule"></div>
    <h3>${locale.lookup("dep.header").replace("%%", spec.getFullyQualifiedName())}</h3>

    @if (spec.getDescription() != null)
        <pre>${spec.getDescription()}</pre>
    @else
        <div>No description available.</div>
    @endif

    <div class="horizontal-rule"></div>
    @template.code_blocks.dependency(spec = spec)
`)