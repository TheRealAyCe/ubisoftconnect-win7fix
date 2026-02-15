#nullable enable

using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;

namespace UbisoftConnectProxy;

public class Startup
{
    public void Configure(IApplicationBuilder app)
    {
        var synchronizer = app.ApplicationServices.GetRequiredService<Synchronizer>();
        IProxy proxy = new JavaProxy(synchronizer);
        app.Use(async (HttpContext context, RequestDelegate next) =>
        {
            await proxy.ForwardAsync(context);
           // await next();
        });
    }
}
