package chaos.xcom.launcher.steam;

import chaos.xcom.launcher.steam.dto.SteamPublishedFileDetailsResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "steam")
@Path("")
public interface SteamClient {


    @POST
    @Path("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    SteamPublishedFileDetailsResponse getPublishedFileDetails(
            @FormParam("itemcount") int itemCount,
            @FormParam("publishedfileids") List<String> publishedFileIds
    );
}
