import { bundledLanguages } from "shiki/langs";
import type { HighlighterCore } from "shiki/core";

const DARK_THEME = "github-dark-default";
const LIGHT_THEME = "github-light-default";

const ALIASES: Record<string, string> = {
  bashrc: "bash",
  cmd: "bat",
  conf: "ini",
  cxx: "cpp",
  ejs: "html",
  env: "dotenv",
  gitattributes: "properties",
  gitconfig: "ini",
  gitignore: "properties",
  h: "c",
  hh: "cpp",
  hpp: "cpp",
  htm: "html",
  m: "objective-c",
  make: "makefile",
  pyi: "python",
  tex: "latex",
};

let pending: Promise<HighlighterCore> | null = null;
const loaded = new Set<string>();

const highlighter = () => {
  pending ??= (async () => {
    const [{ createHighlighterCore }, { createJavaScriptRegexEngine }] = await Promise.all([
      import("shiki/core"),
      import("shiki/engine/javascript"),
    ]);
    return createHighlighterCore({
      themes: [import("@shikijs/themes/github-dark-default"), import("@shikijs/themes/github-light-default")],
      langs: [],
      engine: createJavaScriptRegexEngine(),
    });
  })();
  return pending;
};

export const resolveLang = (lang: string | null | undefined): string | null => {
  if (!lang) return null;
  const normalized = lang.toLowerCase().trim();
  const resolved = ALIASES[normalized] ?? normalized;
  return resolved in bundledLanguages ? resolved : null;
};

export const highlight = async (code: string, lang: string, dark: boolean): Promise<string> => {
  const instance = await highlighter();
  if (!loaded.has(lang)) {
    const grammar = await bundledLanguages[lang as keyof typeof bundledLanguages]();
    await instance.loadLanguage(grammar as Parameters<HighlighterCore["loadLanguage"]>[0]);
    loaded.add(lang);
  }
  return instance.codeToHtml(code, {
    lang,
    theme: dark ? DARK_THEME : LIGHT_THEME,
    structure: "inline",
  });
};
