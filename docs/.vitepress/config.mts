import { defineConfig } from 'vitepress';

export default defineConfig({
  lang: 'en-US',
  title: 'Glue',
  description: 'Documentation for the modular Fabric library.',
  srcDir: 'src/content/docs',
  outDir: 'dist',
  cleanUrls: true,
  lastUpdated: true,
  head: [['link', { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }]],
  markdown: {
    lineNumbers: true,
    config(markdown) {
      const renderFence = markdown.renderer.rules.fence;
      if (!renderFence) return;

      const terminals: Record<string, { label: string; badge: string }> = {
        bash: { label: 'Terminal', badge: 'bash' },
        batch: { label: 'Command Prompt', badge: 'cmd' },
        cmd: { label: 'Command Prompt', badge: 'cmd' },
        console: { label: 'Terminal', badge: 'shell' },
        powershell: { label: 'PowerShell', badge: 'PS' },
        ps1: { label: 'PowerShell', badge: 'PS' },
        sh: { label: 'Terminal', badge: 'sh' },
        shell: { label: 'Terminal', badge: 'shell' },
        zsh: { label: 'Terminal', badge: 'zsh' },
      };

      markdown.renderer.rules.fence = (tokens, index, options, environment, renderer) => {
        const info = tokens[index].info;
        const language = info.trim().split(/[\s:[{]/, 1)[0].toLowerCase();
        const title = info.match(/\[([^\]]+)]/)?.[1];
        const terminal = terminals[language];
        let codeGroupDepth = 0;
        for (let cursor = 0; cursor < index; cursor++) {
          if (tokens[cursor].type === 'container_code-group_open') codeGroupDepth++;
          if (tokens[cursor].type === 'container_code-group_close') codeGroupDepth--;
        }
        if (codeGroupDepth > 0) {
          if (terminal && !info.includes(':no-line-numbers')) {
            tokens[index].info += ':no-line-numbers';
          }
          return renderFence(tokens, index, options, environment, renderer);
        }
        if (terminal && !info.includes(':no-line-numbers')) {
          tokens[index].info += ':no-line-numbers';
        }
        const rendered = renderFence(tokens, index, options, environment, renderer);

        if (terminal) {
          const label = markdown.utils.escapeHtml(title ?? terminal.label);
          return `<figure class="vp-code-window vp-terminal-window">
<figcaption class="vp-terminal-bar">
<span class="vp-terminal-controls" aria-hidden="true"><i></i><i></i><i></i></span>
<span class="vp-terminal-title">${label}</span>
<span class="vp-terminal-kind">${terminal.badge}</span>
</figcaption>
${rendered}</figure>`;
        }
        if (!title) return rendered;

        const filename = title.split(/[\\/]/).at(-1) ?? title;
        const extension = filename.includes('.') ? filename.split('.').at(-1) ?? language : language;
        const escapedTitle = markdown.utils.escapeHtml(title);
        const escapedExtension = markdown.utils.escapeHtml(extension.toUpperCase());
        return `<figure class="vp-code-window vp-file-window" data-extension="${markdown.utils.escapeHtml(extension)}">
<figcaption class="vp-file-bar" title="${escapedTitle}">
<span class="vp-file-extension">${escapedExtension}</span>
<span class="vp-file-name">${escapedTitle}</span>
</figcaption>
${rendered}</figure>`;
      };

      const renderHeadingClose = markdown.renderer.rules.heading_close;
      markdown.renderer.rules.heading_close = (tokens, index, options, environment, renderer) => {
        const rendered = renderHeadingClose
          ? renderHeadingClose(tokens, index, options, environment, renderer)
          : renderer.renderToken(tokens, index, options);
        if (tokens[index].tag !== 'h1') return rendered;

        const frontmatter = environment.frontmatter as Record<string, unknown> | undefined;
        const description = frontmatter?.description;
        if (typeof description !== 'string') return rendered;

        const metadata = [
          ['Artifact', frontmatter.artifact],
          ['Mod ID', frontmatter.modId],
          ['Environment', frontmatter.environment],
        ].flatMap(([label, value]) => {
          const values = Array.isArray(value)
            ? value
            : typeof value === 'string'
              ? value.split(';').map((part) => part.trim())
              : [];
          return values.map((entry) =>
            `<span><b>${label}</b>${markdown.utils.escapeHtml(String(entry))}</span>`);
        });

        const subtitle = `<p class="doc-subtitle" role="doc-subtitle">${markdown.utils.escapeHtml(description)}</p>`;
        const badges = metadata.length > 0 ? `<div class="doc-meta">${metadata.join('')}</div>` : '';
        return `${rendered}${subtitle}${badges}`;
      };

      const renderImage = markdown.renderer.rules.image;
      markdown.renderer.rules.image = (tokens, index, options, environment, renderer) => {
        tokens[index].attrSet('loading', 'lazy');
        tokens[index].attrSet('decoding', 'async');
        return renderImage
          ? renderImage(tokens, index, options, environment, renderer)
          : renderer.renderToken(tokens, index, options);
      };
    },
  },
  themeConfig: {
    siteTitle: 'Glue',
    logo: '/favicon.svg',
    nav: [
      { text: 'Guide', link: '/getting-started' },
      { text: 'Tutorial', link: '/workshop/' },
      { text: 'Modules', link: '/modules' },
      { text: 'Rendering', link: '/rendering/' },
      { text: 'Lumos', link: '/lumos/' },
      { text: 'Web', link: '/web/' },
    ],
    sidebar: [
      {
        text: 'Start Here',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/' },
          { text: 'Install Glue', link: '/getting-started' },
          { text: 'Choose Modules', link: '/modules' },
        ],
      },
      {
        text: 'Light Workshop',
        collapsed: false,
        items: [
          { text: 'Tutorial Overview', link: '/workshop/' },
          { text: 'Build the Lumen Probe', link: '/workshop/probe' },
          { text: 'Add Visual Feedback', link: '/workshop/rendering' },
          { text: 'Light the Probe', link: '/workshop/lighting' },
          { text: 'Test the Workshop', link: '/workshop/testing' },
        ],
      },
      {
        text: 'Core',
        collapsed: true,
        items: [
          { text: 'Core Overview', link: '/core/' },
          { text: 'Items and Data', link: '/core/items' },
          { text: 'Blocks and Block Entities', link: '/core/blocks' },
          { text: 'Add a Keybinding', link: '/core/keybindings' },
          { text: 'Shapes, Math, and History', link: '/core/utilities' },
          { text: 'Registry Reference', link: '/core/registries' },
        ],
      },
      {
        text: 'Rendering',
        collapsed: true,
        items: [
          { text: 'Rendering Overview', link: '/rendering/' },
          { text: 'Choose a Render Event', link: '/rendering/events' },
          { text: 'Customize Block Outlines', link: '/rendering/block-outlines' },
          { text: 'Transform Geometry', link: '/rendering/transforms' },
          { text: 'Render with a Shader', link: '/rendering/pipelines' },
          { text: 'Add a Post Effect', link: '/rendering/post-effects' },
          { text: 'Build a 3D Viewport', link: '/rendering/scene-viewport' },
          { text: 'Open Native File Dialogs', link: '/rendering/file-dialogs' },
          { text: 'Support Optional Mods', link: '/rendering/compatibility' },
          { text: 'Inspect Framebuffers', link: '/rendering/debug-hud' },
        ],
      },
      {
        text: 'Lumos',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/lumos/' },
          { text: 'Lights and Persistence', link: '/lumos/lights' },
          { text: 'Materials and Compatibility', link: '/lumos/materials-and-compatibility' },
        ],
      },
      {
        text: 'Testing',
        collapsed: true,
        items: [
          { text: 'GameTest Overview', link: '/gametest/' },
          { text: 'Set Up and Run', link: '/gametest/setup' },
          { text: 'Write a Test', link: '/gametest/writing' },
          { text: 'Reports and Automation', link: '/gametest/reports' },
        ],
      },
      {
        text: 'Web',
        collapsed: true,
        items: [{ text: 'Browser Surfaces', link: '/web/' }],
      },
    ],
    search: {
      provider: 'local',
    },
    socialLinks: [
      { icon: 'gitlab', link: 'https://gitlab.lacaleche.cc/loccamy/java/glue' },
    ],
    outline: {
      level: [2, 3],
      label: 'On this page',
    },
    docFooter: {
      prev: 'Previous page',
      next: 'Next page',
    },
    footer: {
      message: 'Glue documentation for Minecraft 1.21.8.',
    },
  },
});
