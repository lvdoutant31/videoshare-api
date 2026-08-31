export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
  traceId: string;
};

export async function getHealth(baseUrl: string, path: string): Promise<ApiResponse<{ service: string; status: string }>> {
  const response = await fetch(`${baseUrl}${path}`);
  if (!response.ok) throw new Error(`Health request failed with ${response.status}`);
  return response.json() as Promise<ApiResponse<{ service: string; status: string }>>;
}
