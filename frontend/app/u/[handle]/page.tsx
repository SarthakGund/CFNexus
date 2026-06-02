import type { Metadata } from "next";

interface ProfileParams {
  params: Promise<{ handle: string }>;
}

export async function generateMetadata({
  params,
}: ProfileParams): Promise<Metadata> {
  const { handle } = await params;
  const title = `${handle} — CFNexus profile`;
  const description = `Duel rating, match history, and achievements for ${handle} on CFNexus.`;
  return {
    title,
    description,
    alternates: { canonical: `/u/${handle}` },
    openGraph: {
      title,
      description,
      url: `/u/${handle}`,
      type: "profile",
    },
  };
}

export default async function ProfilePage({ params }: ProfileParams) {
  const { handle } = await params;
  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">{handle}</h1>
      <p className="mt-2 text-muted-foreground">
        Rating graph, match history, and achievement badges land in Phase 3.
      </p>
    </main>
  );
}
