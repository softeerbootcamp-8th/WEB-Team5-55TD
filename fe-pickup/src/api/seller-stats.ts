import { axiosInstance } from "@/api/mutator/custom-instance";

interface SellerStatsResponse {
  registeredConsignments: number;
  scheduledAuctions: number;
  ongoingAuctions: number;
  wonConsignments: number;
}

export interface SellerStats {
  registered: number;
  scheduled: number;
  ongoing: number;
  sold: number;
}

export async function getMySellerStats(): Promise<SellerStats> {
  const { data } = await axiosInstance.get<SellerStatsResponse>(
    "/sellers/me/stats",
  );

  return {
    registered: data.registeredConsignments,
    scheduled: data.scheduledAuctions,
    ongoing: data.ongoingAuctions,
    sold: data.wonConsignments,
  };
}
