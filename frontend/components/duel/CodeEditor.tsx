"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { useTheme } from "next-themes";
import { ExternalLink, Loader2, Play } from "lucide-react";
import api from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

/**
 * Monaco must never render server-side (it touches `window`/`navigator`), so we
 * load it lazily with SSR disabled.
 */
const MonacoEditor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => (
    <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
      <Loader2 className="mr-2 size-4 animate-spin" />
      Loading editor…
    </div>
  ),
});

/* Supported languages (spec §12). `value` is what the backend expects; */
/* `monaco` is the Monaco language id (mostly identical).                */
const LANGUAGES = [
  { value: "cpp", label: "C++", monaco: "cpp" },
  { value: "python", label: "Python", monaco: "python" },
  { value: "java", label: "Java", monaco: "java" },
  { value: "javascript", label: "JavaScript", monaco: "javascript" },
  { value: "go", label: "Go", monaco: "go" },
  { value: "rust", label: "Rust", monaco: "rust" },
] as const;

type LanguageValue = (typeof LANGUAGES)[number]["value"];

/** Starter snippets so the editor is never blank. */
const STARTERS: Record<LanguageValue, string> = {
  cpp: `#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}\n`,
  python: `def main():\n    pass\n\n\nif __name__ == "__main__":\n    main()\n`,
  java: `public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n`,
  javascript: `function main() {\n  \n}\n\nmain();\n`,
  go: `package main\n\nimport "fmt"\n\nfunc main() {\n    _ = fmt.Sprint\n}\n`,
  rust: `fn main() {\n    \n}\n`,
};

interface RunResult {
  stdout: string;
  stderr: string;
  exitCode: number | null;
  executionTimeMs: number | null;
}

export interface CodeEditorProps {
  roomCode: string;
  /** Direct CF problem URL (preferred when available). */
  problemUrl?: string;
  /** CF contest id + problem index, used to build the submit URL as a fallback. */
  contestId?: number | string;
  index?: string;
  className?: string;
}

/**
 * Monaco-backed code editor + Judge0 run panel for the duel room (spec §12).
 * Self-contained: the duel page drops in <CodeEditor … /> directly.
 */
export function CodeEditor({
  roomCode,
  problemUrl,
  contestId,
  index,
  className,
}: CodeEditorProps) {
  const { resolvedTheme } = useTheme();
  const monacoTheme = resolvedTheme === "dark" ? "vs-dark" : "light";

  const [language, setLanguage] = useState<LanguageValue>("cpp");
  const [code, setCode] = useState<string>(STARTERS.cpp);
  const [stdin, setStdin] = useState<string>("");
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RunResult | null>(null);

  const monacoLanguage = useMemo(
    () => LANGUAGES.find((l) => l.value === language)?.monaco ?? "plaintext",
    [language],
  );

  const submitUrl = useMemo(() => {
    if (problemUrl) return problemUrl;
    if (contestId != null && index) {
      return `https://codeforces.com/problemset/problem/${contestId}/${index}`;
    }
    return null;
  }, [problemUrl, contestId, index]);

  function onLanguageChange(next: LanguageValue) {
    // Only replace the buffer if the user hasn't diverged from the old starter.
    setCode((current) =>
      current.trim() === STARTERS[language].trim() ? STARTERS[next] : current,
    );
    setLanguage(next);
  }

  async function run() {
    setRunning(true);
    setError(null);
    setResult(null);
    try {
      const { data } = await api.post<RunResult>("/code/run", {
        language,
        code,
        stdin,
      });
      setResult(data);
    } catch (err: unknown) {
      const status =
        typeof err === "object" && err !== null && "response" in err
          ? (err as { response?: { status?: number } }).response?.status
          : undefined;
      if (status === 429) {
        setError("Run limit reached (10/min). Please wait a moment.");
      } else if (status === 502) {
        setError("Code runner is unavailable. Try again shortly.");
      } else if (status === 400) {
        setError("Invalid submission (check language or code size, max 64KB).");
      } else {
        setError("Failed to run code. Please try again.");
      }
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <label htmlFor="code-language" className="text-sm font-medium">
            Language
          </label>
          <select
            id="code-language"
            value={language}
            onChange={(e) => onLanguageChange(e.target.value as LanguageValue)}
            className="h-9 rounded-md border border-input bg-background px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>
                {l.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2">
          <Button type="button" onClick={run} disabled={running}>
            {running ? <Loader2 className="animate-spin" /> : <Play />}
            {running ? "Running…" : "Run"}
          </Button>
          {submitUrl && (
            <Button asChild>
              <a href={submitUrl} target="_blank" rel="noreferrer">
                <ExternalLink />
                Submit on Codeforces
              </a>
            </Button>
          )}
        </div>
      </div>

      {/* Editor */}
      <div className="h-[50vh] w-full overflow-hidden rounded-md border">
        <MonacoEditor
          height="100%"
          language={monacoLanguage}
          theme={monacoTheme}
          value={code}
          onChange={(val) => setCode(val ?? "")}
          options={{
            fontSize: 14,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            automaticLayout: true,
          }}
        />
      </div>

      {/* Stdin */}
      <div className="flex flex-col gap-1">
        <label htmlFor="code-stdin" className="text-sm font-medium">
          Standard input (stdin)
        </label>
        <textarea
          id="code-stdin"
          value={stdin}
          onChange={(e) => setStdin(e.target.value)}
          placeholder="Optional input passed to your program…"
          className="h-20 w-full resize-y rounded-md border bg-muted/30 p-2 font-mono text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      {/* Output */}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {result && (
        <div
          aria-live="polite"
          className="space-y-2 rounded-md border bg-card p-3 text-sm"
        >
          <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
            <span>
              Exit code:{" "}
              <span
                className={cn(
                  "font-mono font-semibold",
                  result.exitCode === 0 ? "text-green-500" : "text-destructive",
                )}
              >
                {result.exitCode ?? "n/a"}
              </span>
            </span>
            {result.executionTimeMs != null && (
              <span>
                Time:{" "}
                <span className="font-mono font-semibold">
                  {result.executionTimeMs}ms
                </span>
              </span>
            )}
          </div>

          <div>
            <p className="text-xs font-medium text-muted-foreground">stdout</p>
            <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-muted/40 p-2 font-mono text-xs">
              {result.stdout || "(empty)"}
            </pre>
          </div>

          {result.stderr && (
            <div>
              <p className="text-xs font-medium text-destructive">stderr</p>
              <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-destructive/10 p-2 font-mono text-xs text-destructive">
                {result.stderr}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default CodeEditor;
